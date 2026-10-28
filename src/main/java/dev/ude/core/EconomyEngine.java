package dev.ude.core;

import java.util.*;

/**
 * Server-authoritative, deterministic financial rules. No Minecraft classes: testable offline.
 * Money units are integers. All calls must be performed on the logical server thread.
 */
public final class EconomyEngine {
    public static final long DAY_TICKS = 24_000L;
    public static final long COLLECTOR_DELAY = 3L * DAY_TICKS;

    public static final class Loan {
        public final UUID id, owner;
        public final LoanType type;
        public final long principal, createdAt, dueAt;
        public long outstanding;
        public boolean collectorDispatched;
        private Loan(UUID id,UUID owner,LoanType type,long principal,long outstanding,long createdAt,long dueAt,boolean dispatched) {
            this.id=id;this.owner=owner;this.type=type;this.principal=principal;this.outstanding=outstanding;
            this.createdAt=createdAt;this.dueAt=dueAt;this.collectorDispatched=dispatched;
        }
        public boolean overdue(long now) {return outstanding>0 && now>dueAt;}
        public boolean needsCollector(long now) {
            return outstanding>0 && !collectorDispatched && now>=Math.addExact(dueAt,COLLECTOR_DELAY);
        }
    }
    public static final class Business {
        public final UUID id, owner;
        public final BusinessType type;
        public boolean completed;
        public long grossRevenue, costs, taxDebt;
        private Business(UUID id, UUID owner,BusinessType type,boolean completed,long grossRevenue,long costs,long taxDebt) {
            this.id=id;this.owner=owner;this.type=type;this.completed=completed;
            this.grossRevenue=grossRevenue;this.costs=costs;this.taxDebt=taxDebt;
        }
    }
    public record Home(String dimension,int x,int y,int z) {}
    private final Map<UUID,Long> balances=new HashMap<>();
    private final Map<UUID,Loan> loans=new LinkedHashMap<>();
    private final Map<UUID,Business> businesses=new LinkedHashMap<>();
    private final Map<UUID,Home> homes=new HashMap<>();
    private final Set<String> performed=new HashSet<>();

    public synchronized boolean hasAccount(UUID id) { return balances.containsKey(id); }
    public synchronized boolean openAccount(UUID id) { Objects.requireNonNull(id); return balances.putIfAbsent(id,0L)==null; }
    public synchronized long balance(UUID id) { return balances.getOrDefault(id,0L); }
    private void requireAccount(UUID id) { if(!hasAccount(id)) throw new IllegalStateException("Open an account first"); }
    private static void positive(long amount) { if(amount<=0) throw new IllegalArgumentException("Amount must be positive"); }
    private static void ticket(String id) { if(id==null || id.isBlank() || id.indexOf('\t')>=0 || id.indexOf('\n')>=0) throw new IllegalArgumentException("Invalid operation id"); }

    /** Admin-granted credits or payment for verified consumed in-world goods. */
    public synchronized boolean grant(UUID owner,long amount,String id) {
        requireAccount(owner);positive(amount);ticket(id);
        if(performed.contains(id))return false;
        long next=Math.addExact(balance(owner),amount);
        balances.put(owner,next);performed.add(id);return true;
    }
    /** Debit existing credits for physical coin withdrawal or a verified purchase. */
    public synchronized boolean debit(UUID owner,long amount,String id) {
        requireAccount(owner);positive(amount);ticket(id);
        if(performed.contains(id))return false;
        if(balance(owner)<amount)throw new IllegalStateException("Insufficient credits");
        balances.put(owner,balance(owner)-amount);performed.add(id);return true;
    }
    public synchronized boolean transfer(UUID from,UUID to,long amount,String id) {
        requireAccount(from);requireAccount(to);positive(amount);ticket(id);
        if(performed.contains(id))return false;
        if(from.equals(to)) throw new IllegalArgumentException("Transfer to self");
        if(balance(from)<amount) throw new IllegalStateException("Insufficient credits");
        long nextReceiver=Math.addExact(balance(to),amount);
        balances.put(from,balance(from)-amount);balances.put(to,nextReceiver);performed.add(id);return true;
    }
    public synchronized Loan takeLoan(UUID owner,LoanType type,long principal,long now,long days,String id) {
        requireAccount(owner);Objects.requireNonNull(type);positive(principal);ticket(id);
        if(performed.contains(id)) throw new IllegalStateException("Loan request already processed");
        if(principal<type.min || principal>type.max) throw new IllegalArgumentException("Out of range for "+type);
        if(days<1 || days>120) throw new IllegalArgumentException("Loan duration must be 1..120 Minecraft days");
        if(loans.values().stream().filter(l->l.owner.equals(owner)&&l.outstanding>0).count()>=3)
            throw new IllegalStateException("Maximum three active loans");
        long fee=(Math.addExact(Math.multiplyExact(principal,type.feePercent),99L))/100L;
        long owed=Math.addExact(principal,fee);
        long due=Math.addExact(now,Math.multiplyExact(days,DAY_TICKS));
        long newBalance=Math.addExact(balance(owner),principal);
        Loan loan=new Loan(UUID.randomUUID(),owner,type,principal,owed,now,due,false);
        loans.put(loan.id,loan); balances.put(owner,newBalance);performed.add(id);return loan;
    }
    public synchronized long repay(UUID owner,UUID loanId,long requested,String id) {
        requireAccount(owner);positive(requested);ticket(id);
        if(performed.contains(id))return 0;
        Loan l=loans.get(loanId);
        if(l==null||!l.owner.equals(owner)) throw new IllegalArgumentException("Loan not owned by player");
        long paid=Math.min(requested,Math.min(l.outstanding,balance(owner)));
        if(paid==0)throw new IllegalStateException("Nothing to repay or insufficient funds");
        l.outstanding-=paid;balances.put(owner,balance(owner)-paid);performed.add(id);return paid;
    }
    /** Applies market value of actual seized game resources; caller must verify inventory ownership/removal. */
    public synchronized long settleWithGoods(UUID owner,UUID loanId,long value,String id) {
        positive(value);ticket(id);
        if(performed.contains(id))return 0;
        Loan l=loans.get(loanId);
        if(l==null||!l.owner.equals(owner))throw new IllegalArgumentException("Loan not owned");
        long credited=Math.min(value,l.outstanding);
        if(credited==0)throw new IllegalStateException("Loan already repaid");
        long refund=value-credited;
        if(refund>0)balances.put(owner,Math.addExact(balance(owner),refund));
        l.outstanding-=credited;performed.add(id);return credited;
    }
    /** Returns loans that become eligible for dispatch. The dispatch flag is persisted. */
    public synchronized List<Loan> markCollectorsDue(long now) {
        List<Loan> result=new ArrayList<>();
        for(Loan l:loans.values())if(l.needsCollector(now)) {l.collectorDispatched=true;result.add(l);}
        return result;
    }
    public synchronized Collection<Loan> loans() {return List.copyOf(loans.values());}
    public synchronized Collection<Business> businesses(){return List.copyOf(businesses.values());}
    public synchronized Loan getLoan(UUID id){return loans.get(id);}
    public synchronized Business getBusiness(UUID id){return businesses.get(id);}
    public synchronized void setHome(UUID player,Home h){homes.put(player,Objects.requireNonNull(h));}
    public synchronized Home getHome(UUID player){return homes.get(player);}
    public synchronized Business registerBusiness(UUID owner,BusinessType type,String id){
        requireAccount(owner);ticket(id);Objects.requireNonNull(type);
        if(performed.contains(id))throw new IllegalStateException("Business registration already processed");
        long price=type.purchasePrice;
        if(balance(owner)<price)throw new IllegalStateException("Insufficient credits");
        Business b=new Business(UUID.randomUUID(),owner,type,false,0,0,0);
        businesses.put(b.id,b);balances.put(owner,balance(owner)-price);performed.add(id);return b;
    }
    public synchronized void finishConstruction(UUID owner,UUID businessId){
        Business b=businesses.get(businessId);
        if(b==null || !b.owner.equals(owner))throw new IllegalArgumentException("Business not owned");
        b.completed=true; // Caller validates actual blueprint before invoking; admins may override.
    }
    public synchronized boolean addBusinessSale(UUID owner,UUID businessId,long revenue,long productionCost,String id){
        requireAccount(owner);positive(revenue);
        if(productionCost<0)throw new IllegalArgumentException("Negative cost");
        ticket(id);if(performed.contains(id))return false;
        Business b=businesses.get(businessId);
        if(b==null||!b.owner.equals(owner)||!b.completed)throw new IllegalStateException("Inactive business");
        if(balance(owner)<productionCost)throw new IllegalStateException("Insufficient money for costs");
        long next=Math.addExact(balance(owner)-productionCost,revenue);
        long profit=Math.max(0,revenue-productionCost);
        long tax=(Math.multiplyExact(profit,8)+99)/100;
        long nextTax=Math.addExact(b.taxDebt,tax);
        long nextGross=Math.addExact(b.grossRevenue,revenue);
        long nextCost=Math.addExact(b.costs,productionCost);
        balances.put(owner,next);b.taxDebt=nextTax;b.grossRevenue=nextGross;b.costs=nextCost;
        performed.add(id);return true;
    }
    public synchronized long payTaxes(UUID owner,UUID businessId,long requested,String id){
        requireAccount(owner);positive(requested);ticket(id);
        if(performed.contains(id))return 0;
        Business b=businesses.get(businessId);
        if(b==null||!b.owner.equals(owner))throw new IllegalArgumentException("Business not owned");
        long paid=Math.min(requested,Math.min(balance(owner),b.taxDebt));
        if(paid==0)throw new IllegalStateException("No tax due or insufficient funds");
        balances.put(owner,balance(owner)-paid);b.taxDebt-=paid;performed.add(id);return paid;
    }

    /** Compact tab-delimited data for SavedData NBT; versions are intentionally explicit. */
    public synchronized String exportText() {
        StringBuilder b=new StringBuilder("UDE1\n");
        balances.forEach((id,v)->b.append("A\t").append(id).append('\t').append(v).append('\n'));
        for(Loan l:loans.values())b.append("L\t").append(l.id).append('\t').append(l.owner).append('\t').append(l.type)
          .append('\t').append(l.principal).append('\t').append(l.outstanding).append('\t').append(l.createdAt)
          .append('\t').append(l.dueAt).append('\t').append(l.collectorDispatched).append('\n');
        for(Business v:businesses.values())b.append("B\t").append(v.id).append('\t').append(v.owner).append('\t').append(v.type)
          .append('\t').append(v.completed).append('\t').append(v.grossRevenue).append('\t').append(v.costs).append('\t').append(v.taxDebt).append('\n');
        homes.forEach((id,v)->b.append("H\t").append(id).append('\t').append(v.dimension).append('\t').append(v.x)
          .append('\t').append(v.y).append('\t').append(v.z).append('\n'));
        performed.forEach(id->b.append("T\t").append(id).append('\n'));
        return b.toString();
    }
    public static EconomyEngine importText(String text){
        EconomyEngine e=new EconomyEngine();
        if(text==null||text.isEmpty())return e;
        String[] lines=text.split("\\R");
        if(!lines[0].equals("UDE1"))throw new IllegalArgumentException("Unknown save schema");
        for(int i=1;i<lines.length;i++){
            if(lines[i].isBlank())continue;
            String[] a=lines[i].split("\\t",-1);
            try {
                switch(a[0]){
                    case "A" -> {long n=Long.parseLong(a[2]);if(n<0)throw new IllegalArgumentException();e.balances.put(UUID.fromString(a[1]),n);}
                    case "L" -> {Loan l=new Loan(UUID.fromString(a[1]),UUID.fromString(a[2]),LoanType.valueOf(a[3]),Long.parseLong(a[4]),Long.parseLong(a[5]),Long.parseLong(a[6]),Long.parseLong(a[7]),Boolean.parseBoolean(a[8]));e.loans.put(l.id,l);}
                    case "B" -> {Business v=new Business(UUID.fromString(a[1]),UUID.fromString(a[2]),BusinessType.valueOf(a[3]),Boolean.parseBoolean(a[4]),Long.parseLong(a[5]),Long.parseLong(a[6]),Long.parseLong(a[7]));e.businesses.put(v.id,v);}
                    case "H" -> e.homes.put(UUID.fromString(a[1]),new Home(a[2],Integer.parseInt(a[3]),Integer.parseInt(a[4]),Integer.parseInt(a[5])));
                    case "T" -> e.performed.add(a[1]);
                    default -> throw new IllegalArgumentException("Unknown record type");
                }
            }catch(RuntimeException ex){throw new IllegalArgumentException("Corrupt save entry at line "+(i+1),ex);}
        }
        return e;
    }
}
