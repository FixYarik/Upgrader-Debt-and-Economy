package dev.ude.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** First interactive GUI; sends the same authenticated Brigadier commands as chat. */
public final class UdePhoneScreen extends Screen {
    public UdePhoneScreen(){super(Component.literal("UDE Smartphone"));}
    @Override protected void init(){
        int x=width/2-84,y=height/2-95;
        addButton(x,y,"БАНК / BANK","ude bank balance");
        addButton(x,y+27,"КРЕДИТЫ / LOANS","ude credit list");
        addButton(x,y+54,"БИЗНЕС / BUSINESS","ude business list");
        addButton(x,y+81,"РЫНОК / MARKET","ude market prices");
        addButton(x,y+108,"ПОМОЩЬ / HELP","ude help");
        addRenderableWidget(Button.builder(Component.literal("Закрыть / Close"),b->onClose())
           .bounds(x,y+135,168,20).build());
    }
    private void addButton(int x,int y,String label,String command){
        addRenderableWidget(Button.builder(Component.literal(label),b->{
            if(Minecraft.getInstance().getConnection()!=null)
                Minecraft.getInstance().getConnection().sendCommand(command);
            onClose();
        }).bounds(x,y,168,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial){
        renderBackground(graphics);
        graphics.fill(width/2-96,height/2-122,width/2+96,height/2+82,0xE9121D2F);
        graphics.drawCenteredString(font,"UDE SMARTPHONE",width/2,height/2-113,0xF1C96A);
        super.render(graphics,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
}
