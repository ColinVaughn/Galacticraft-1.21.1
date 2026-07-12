/*
 * Copyright (c) 2019-2026 Team Galacticraft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.mod.util;

import dev.galacticraft.machinelib.api.storage.StorageAccess;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/** Loader-neutral MachineLib/Fabric Transfer API interop used by both platform modules. */
public final class TransferStorageUtil {
    private TransferStorageUtil() {
    }

    public static <Variant extends TransferVariant<?>> long theoreticalCapacity(Storage<Variant> storage) {
        long capacity = 0;
        for (StorageView<Variant> view : storage) {
            capacity += view.getCapacity();
        }
        return capacity;
    }

    public static <Variant extends TransferVariant<?>> long amountOf(Variant variant, Storage<Variant> storage) {
        long amount = 0;
        for (StorageView<Variant> view : storage.nonEmptyViews()) {
            if (variant.equals(view.getResource())) {
                amount += view.getAmount();
            }
        }
        return amount;
    }

    public static <Resource, Variant extends TransferVariant<Resource>> long move(
            Variant variant, @Nullable StorageAccess<Resource> source, @Nullable Storage<Variant> target, long limit) {
        if (source == null || target == null || variant.isBlank() || limit <= 0) {
            return 0;
        }

        long available = source.tryExtract(variant.getObject(), variant.getComponents(), limit);
        try (Transaction transaction = Transaction.openOuter()) {
            long accepted = target.insert(variant, available, transaction);
            if (accepted > 0 && source.tryExtract(variant.getObject(), variant.getComponents(), accepted) == accepted) {
                source.extract(variant.getObject(), variant.getComponents(), accepted);
                transaction.commit();
                return accepted;
            }
        }
        return 0;
    }

    public static <Resource, Variant extends TransferVariant<Resource>> long move(
            Variant variant, @Nullable Storage<Variant> source, @Nullable StorageAccess<Resource> target, long limit) {
        if (source == null || target == null || variant.isBlank() || limit <= 0) {
            return 0;
        }

        long available = target.tryInsert(variant.getObject(), variant.getComponents(), limit);
        try (Transaction transaction = Transaction.openOuter()) {
            long extracted = source.extract(variant, available, transaction);
            if (extracted > 0 && target.tryInsert(variant.getObject(), variant.getComponents(), extracted) == extracted) {
                target.insert(variant.getObject(), variant.getComponents(), extracted);
                transaction.commit();
                return extracted;
            }
        }
        return 0;
    }
}
