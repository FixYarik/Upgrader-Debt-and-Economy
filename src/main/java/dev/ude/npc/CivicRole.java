package dev.ude.npc;

import net.minecraft.world.entity.npc.VillagerProfession;

public enum CivicRole {
    MAYOR(0,8,18,VillagerProfession.LIBRARIAN),
    BANKER(-14,1,-18,VillagerProfession.CARTOGRAPHER),
    SENIOR_BANKER(14,1,-18,VillagerProfession.CARTOGRAPHER),
    BUSINESS(14,8,18,VillagerProfession.LEATHERWORKER),
    TAX(-14,8,18,VillagerProfession.LIBRARIAN),
    GUARD(0,-4,8,VillagerProfession.ARMORER),
    VALERA(-16,1,4,VillagerProfession.TOOLSMITH),
    DIMON(-13,1,4,VillagerProfession.TOOLSMITH),
    SANYA(-10,1,4,VillagerProfession.TOOLSMITH);
    public final int x,y,z;
    public final VillagerProfession profession;
    CivicRole(int x,int y,int z,VillagerProfession profession){this.x=x;this.y=y;this.z=z;this.profession=profession;}
    public String key(){return "npc.upgrader_debt_economy."+name().toLowerCase(java.util.Locale.ROOT);}
    public boolean banker(){return this==BANKER||this==SENIOR_BANKER;}
    public boolean patrol(){return this==GUARD||this==VALERA||this==DIMON||this==SANYA;}
}
