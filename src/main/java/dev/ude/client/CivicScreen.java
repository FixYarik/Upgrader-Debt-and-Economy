package dev.ude.client;

import dev.ude.menu.CivicMenu;
import dev.ude.npc.CivicRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CivicScreen extends AbstractContainerScreen<CivicMenu> {
    public CivicScreen(CivicMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=250;imageHeight=234;}
    private Component text(String key){return Component.translatable("menu.upgrader_debt_economy."+key);}
    @Override protected void init(){
        super.init();int y=topPos+64;
        if(menu.role().banker()){
            button(y,"open_account",0);button(y+24,"deposit",1);button(y+48,"withdraw",2);button(y+72,"loan",3);button(y+96,"repay",4);
        }else if(menu.role()==CivicRole.MAYOR){button(y+24,"guide",10);}
        else if(menu.role()==CivicRole.BUSINESS){
            String[] types={"farm","shop","mine","logistics","factory"};
            for(int i=0;i<types.length;i++)button(y+24*i,types[i],20+i);
        }else if(menu.role()==CivicRole.TAX){button(y+24,"pay_tax",30);}
        addRenderableWidget(Button.builder(text("close"),b->onClose()).bounds(leftPos+16,topPos+204,218,20).build());
    }
    private void button(int y,String key,int action){
        addRenderableWidget(Button.builder(text(key),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);})
            .bounds(leftPos+16,y,218,20).build());
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF0121D2F);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+3,0xFFF1C96A);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawCenteredString(font,title,imageWidth/2,13,0xF1C96A);
        g.drawString(font,Component.translatable("menu.upgrader_debt_economy.balance",menu.balance()),16,34,0xFFFFFF,false);
        if(menu.role().banker())g.drawString(font,Component.translatable("menu.upgrader_debt_economy.debt",menu.debt()),16,47,0xFFFFFF,false);
        else if(menu.role()!=CivicRole.BUSINESS)g.drawWordWrap(font,Component.translatable(menu.role().key()+".info"),16,48,218,0xD6E1EB);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
