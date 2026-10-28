package dev.ude.core;
import java.util.*;

/** Run standalone using scripts/test_core.py; no external dependencies. */
public final class EconomyEngineTest {
    static int passed;
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);passed++;}
    static void throwsError(Runnable task,String label){try{task.run();throw new AssertionError(label);}catch(IllegalArgumentException|IllegalStateException expected){passed++;}}
    public static void main(String[] args){
        EconomyEngine e=new EconomyEngine();UUID anna=UUID.randomUUID(),boris=UUID.randomUUID();
        check(e.openAccount(anna),"new account");check(!e.openAccount(anna),"no duplicated account");
        check(e.openAccount(boris),"second account");check(e.grant(anna,3000,"grant1"),"admin grant");
        check(!e.grant(anna,3000,"grant1"),"idempotent grant");
        check(e.balance(anna)==3000,"grant credited once");
        check(e.transfer(anna,boris,500,"t1"),"transfer");
        check(!e.transfer(anna,boris,500,"t1"),"transfer idempotent");
        check(e.balance(anna)==2500 && e.balance(boris)==500,"balances");
        throwsError(()->e.transfer(boris,anna,1000,"bad"),"insufficient funds");
        EconomyEngine.Loan loan=e.takeLoan(anna,LoanType.MICRO,1000,200,5,"loan1");
        check(loan.outstanding==1150,"15% fixed total fee");
        check(e.balance(anna)==3500,"principal received");
        check(e.markCollectorsDue(loan.dueAt+EconomyEngine.COLLECTOR_DELAY-1).isEmpty(),"not dispatched early");
        check(e.repay(anna,loan.id,400,"repay1")==400,"partial repay");
        check(loan.outstanding==750,"remaining debt");
        check(e.markCollectorsDue(loan.dueAt+EconomyEngine.COLLECTOR_DELAY,l->false).isEmpty(),"ineligible owner waits");
        check(!loan.collectorDispatched,"waiting owner does not consume dispatch");
        EconomyEngine pending=EconomyEngine.importText(e.exportText());
        check(pending.markCollectorsDue(loan.dueAt+EconomyEngine.COLLECTOR_DELAY,l->l.owner.equals(anna)).size()==1,"pending dispatch survives reload and eligibility change");
        check(e.markCollectorsDue(loan.dueAt+EconomyEngine.COLLECTOR_DELAY).size()==1,"dispatch exactly at threshold");
        check(e.markCollectorsDue(loan.dueAt+EconomyEngine.COLLECTOR_DELAY).isEmpty(),"no duplicate dispatch");
        e.setHome(anna,new EconomyEngine.Home("minecraft:overworld",2,64,10));
        String saved=e.exportText(); EconomyEngine restored=EconomyEngine.importText(saved);
        check(restored.exportText().contains("UDE1"),"schema marker");
        check(restored.balance(anna)==3100,"balance survives save");
        check(restored.getHome(anna).x()==2,"home survives save");
        check(restored.getLoan(loan.id).collectorDispatched,"dispatch survives save");
        check(!restored.grant(anna,3000,"grant1"),"idempotency survives save");
        check(restored.settleWithGoods(anna,loan.id,800,"seize1")==750,"goods settle remaining debt");
        check(restored.balance(anna)==3150,"overpayment refunded");
        check(restored.getLoan(loan.id).outstanding==0,"loan completely repaid");
        EconomyEngine.Business farm=restored.registerBusiness(anna,BusinessType.FARM,"biz1");
        check(restored.balance(anna)==1150,"business purchase deducted");
        throwsError(()->restored.addBusinessSale(anna,farm.id,1000,100,"sale0"),"cannot sell before construction");
        restored.finishConstruction(anna,farm.id);
        check(restored.addBusinessSale(anna,farm.id,1000,100,"sale1"),"production sale");
        check(farm.taxDebt==72,"tax is 8 percent of gross profit");
        check(restored.payTaxes(anna,farm.id,72,"tax1")==72,"pay taxes");
        check(farm.taxDebt==0,"tax debt cleared");
        check(restored.getBusiness(farm.id)!=null,"registered business");
        throwsError(()->restored.takeLoan(boris,LoanType.MICRO,9000,0,5,"badloan"),"loan bounds enforced");
        check(restored.debit(anna,100,"withdraw-test"),"withdraw debits balance");
        check(restored.balance(anna)>0,"withdraw kept nonnegative");
        check(!restored.debit(anna,100,"withdraw-test"),"idempotent withdraw");
        throwsError(()->restored.debit(anna,Long.MAX_VALUE,"huge-withdraw"),"withdraw funds check");
        check(EconomyEngine.importText(restored.exportText()).getBusiness(farm.id).completed,"business state survives save");
        check(EconomyEngine.importText(restored.exportText()).getBusiness(farm.id).taxDebt==0,"tax state survives save");
        check(!restored.getLoan(loan.id).needsCollector(Long.MAX_VALUE),"paid loan does not dispatch");
        System.out.println("PASS: "+passed+" core assertions");
    }
}
