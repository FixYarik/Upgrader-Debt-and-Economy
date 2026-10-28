package dev.ude.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/** Actual Minecraft written book with 15 pages for in-game onboarding. */
public final class MayorGuide {
    private MayorGuide(){}
    private static final List<String> PAGES=List.of(
        "1. Добро пожаловать! UDE добавляет банки, долги и предприятия. Сначала откройте счёт: /ude bank open.",
        "2. Банковский счёт. /ude bank balance показывает баланс; /ude bank transfer отправляет игровые кредиты другу.",
        "3. Заработок. 1 физическая монета = 100 игровых кредитов. Чтобы положить монеты, введите /ude bank deposit <количество>.",
        "4. Кредиты. /ude credit take MICRO 500 5 выдаёт микрозайм на пять игровых дней. Процент фиксирован за весь срок.",
        "5. Просрочка. Через 72 000 игровых тиков после дедлайна формируется отправка спортиков. Лучше оплатить вовремя.",
        "6. Спортики. В текущей ранней версии доступно уведомление о визите. Преследование и изъятие предметов ещё в разработке.",
        "7. Откройте бизнес. /ude business buy FARM покупает фермерскую лицензию за 2000 кредитов.",
        "8. Строительство. Рядом с вами может появиться фундамент. Для завершения нужны дубовые опоры, балки крыши и рабочие блоки.",
        "9. Рабочие. Будущая система найма позволит развивать предприятия и выплачивать им игровую зарплату.",
        "10. Производство. Предприятия будут преобразовывать ресурсы и продавать товары. Текущая версия включает учёт состояния.",
        "11. Торговля. Цена товаров рассчитывается на сервере; будущая интеграция с Upgrader Items позволит использовать сложные оценки.",
        "12. Налоги. Прибыльный бизнес платит игровые сборы, предусмотренные экономической моделью.",
        "13. Улучшение бизнеса. Более поздние версии добавят склад, рабочие места и повышение производительности.",
        "14. Смартфон UDE. Правый клик открывает GUI быстрых команд: баланс, кредиты, предприятия и справка.",
        "15. Upgrader Items. Оригинальный мод оценивает предметы по рецептам. В UDE пока реализована независимая модель стоимости."
    );
    public static ItemStack make(){
        ItemStack result=new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag=result.getOrCreateTag();
        tag.putString("title","Путеводитель по UDE");tag.putString("author","Мэр");
        ListTag pages=new ListTag();
        for(String page:PAGES)pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        tag.put("pages",pages);
        return result;
    }
}
