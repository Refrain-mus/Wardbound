
package dev.marrowseal.wardbound;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-only Curios bridge so Curios stays optional for Wardbound.
 * Uses public Curios interfaces and falls back to scanning the handler.
 */
public final class CuriosCompat {

    private CuriosCompat() {
    }

    public static ItemStack findFirstEquipped(LivingEntity entity, Item item) {
        if (!ModList.get().isLoaded("curios")) return ItemStack.EMPTY;
        try {
            Class<?> apiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handlerInterface = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> stacksInterface = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> itemHandlerInterface = Class.forName("net.minecraftforge.items.IItemHandler");

            Method getCuriosInventory = apiClass.getMethod("getCuriosInventory", LivingEntity.class);
            Object lazy = getCuriosInventory.invoke(null, entity);
            if (!(lazy instanceof LazyOptional<?> lazyOptional)) return ItemStack.EMPTY;
            Optional<?> resolved = lazyOptional.resolve();
            if (resolved.isEmpty()) return ItemStack.EMPTY;
            Object handler = resolved.get();

            // Curios 5.x direct search, when present.
            try {
                Method findFirst = handlerInterface.getMethod("findFirstCurio", Item.class);
                Object result = findFirst.invoke(handler, item);
                if (result instanceof Optional<?> optional && optional.isPresent()) {
                    Object slotResult = optional.get();
                    Method stackAccessor = slotResult.getClass().getMethod("stack");
                    Object stackObj = stackAccessor.invoke(slotResult);
                    if (stackObj instanceof ItemStack stack && !stack.isEmpty()) return stack;
                }
            } catch (Throwable ignored) {
                // Fall through to handler scan for other Curios 1.20.1 builds.
            }

            Method getCurios = handlerInterface.getMethod("getCurios");
            Object curiosObj = getCurios.invoke(handler);
            if (!(curiosObj instanceof Map<?, ?> curios)) return ItemStack.EMPTY;
            for (Object stacksHandler : curios.values()) {
                Method getStacks = stacksInterface.getMethod("getStacks");
                Object stacks = getStacks.invoke(stacksHandler);
                Method getSlots = itemHandlerInterface.getMethod("getSlots");
                Method getStackInSlot = itemHandlerInterface.getMethod("getStackInSlot", int.class);
                int slots = (Integer) getSlots.invoke(stacks);
                for (int i = 0; i < slots; i++) {
                    Object stackObj = getStackInSlot.invoke(stacks, i);
                    if (stackObj instanceof ItemStack stack && !stack.isEmpty() && stack.is(item)) {
                        return stack;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Optional integration: never let a Curios API mismatch break Wardbound itself.
        }
        return ItemStack.EMPTY;
    }
}
