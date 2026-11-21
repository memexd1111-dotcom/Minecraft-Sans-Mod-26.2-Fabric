package com.benja.sansmod;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Consumables;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

public final class ModItems {
    private static final FoodProperties HOT_DOG_FOOD = new FoodProperties.Builder()
            .nutrition(6)
            .saturationModifier(0.8f)
            .alwaysEdible()
            .build();

    private static final Consumable HOT_DOG_CONSUMABLE = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(
                    new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 1), 1.0f))
            .build();

    /** El objeto que da las habilidades. */
    public static final Item SANS_CHARM = register("sans_charm", Item::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public static final Item HOT_DOG = register("hot_dog", Item::new,
            new Item.Properties().food(HOT_DOG_FOOD, HOT_DOG_CONSUMABLE));

    // Objetos "de adorno": solo se usan como modelo en las cabezas de los armor stands invisibles.
    public static final Item BONE_PROJECTILE = register("bone_projectile", Item::new, new Item.Properties());
    public static final Item BONE_WALL = register("bone_wall", Item::new, new Item.Properties());
    public static final Item BLASTER_SKULL = register("blaster_skull", Item::new, new Item.Properties());

    private ModItems() {}

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SansMod.id(name));
        Item item = factory.apply(props.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(tab -> tab.accept(SANS_CHARM));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(tab -> tab.accept(HOT_DOG));
    }
}
