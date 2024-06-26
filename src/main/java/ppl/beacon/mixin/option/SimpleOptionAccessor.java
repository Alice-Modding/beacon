package ppl.beacon.mixin.option;

import com.mojang.serialization.Codec;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {
    @Accessor
    @Mutable
    void setCodec(Codec<T> value);
}
