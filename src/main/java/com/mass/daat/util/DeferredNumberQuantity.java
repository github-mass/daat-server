package com.mass.daat.util;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import tech.units.indriya.AbstractQuantity;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.internal.function.Calculator;
import tech.units.indriya.internal.function.ScaleHelper;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/*
    Not used yet. Idea is to bundle requests to IGN service to avoid 429 rejections.
    I've implemented a small timeout in response to 429's, but I doubt that's a definitive solution.

    Instead of a direct resolution, a BatchingIgnService would return a deferred quantity.
    Actual request would be made when Quantity#getValue is accessed, or the max. number of quantities are queued up
    (currently 5000 on the service).

    getValue would be accessed when extracted entities are converted to DB entities. That process would need
    to be adjusted to account for resolution failures; I'm not quite sure how, yet.
 */
public class DeferredNumberQuantity<Q extends DeferredNumberQuantity<Q>>
    extends AbstractQuantity<Q>
{
    @NonNull
    private final Future<Number> deferred;
    @NonNull
    private final ResolverProperties resolverProperties;

    private volatile Number adjustedResult;

    public DeferredNumberQuantity(@NotNull Future<Number> deferred, Unit<Q> unit, Scale sc, @NonNull ResolverProperties resolverProperties) {
        super(unit, sc);
        this.deferred = deferred;
        this.resolverProperties = resolverProperties;
    }

    public DeferredNumberQuantity(Future<Number> number, Unit<Q> unit, @NonNull ResolverProperties resolverProperties) {
        this(number, unit, Scale.ABSOLUTE, resolverProperties);
    }

    public ComparableQuantity<Q> add(Quantity<Q> that) {
        return ScaleHelper.addition(this, that, (thisValue, thatValue) -> {
            return Calculator.of(thisValue).add(thatValue).peek();
        });
    }

    public ComparableQuantity<Q> subtract(Quantity<Q> that) {
        return ScaleHelper.addition(this, that, (thisValue, thatValue) -> {
            return Calculator.of(thisValue).subtract(thatValue).peek();
        });
    }

    public ComparableQuantity<?> divide(Quantity<?> that) {
        return ScaleHelper.multiplication(this, that, (thisValue, thatValue) -> {
            return Calculator.of(thisValue).divide(thatValue).peek();
        }, Unit::divide);
    }

    public ComparableQuantity<Q> divide(Number divisor) {
        return ScaleHelper.scalarMultiplication(this, (thisValue) -> {
            return Calculator.of(thisValue).divide(divisor).peek();
        });
    }

    public ComparableQuantity<?> multiply(Quantity<?> that) {
        return ScaleHelper.multiplication(this, that, (thisValue, thatValue) -> {
            return Calculator.of(thisValue).multiply(thatValue).peek();
        }, Unit::multiply);
    }

    public ComparableQuantity<Q> multiply(Number factor) {
        return ScaleHelper.scalarMultiplication(this, (thisValue) -> {
            return Calculator.of(thisValue).multiply(factor).peek();
        });
    }

    public ComparableQuantity<?> inverse() {
        Number resultValueInThisUnit = Calculator.of(this.getValue()).reciprocal().peek();
        return Quantities.getQuantity(resultValueInThisUnit, this.getUnit().inverse(), this.getScale());
    }

    public Quantity<Q> negate() {
        Number resultValueInThisUnit = Calculator.of(this.getValue()).negate().peek();
        return Quantities.getQuantity(resultValueInThisUnit, this.getUnit(), this.getScale());
    }

    @SneakyThrows
    public Number getValue() {
        if(adjustedResult != null){
            return adjustedResult;
        }
        else {
            Number n = deferred.get(resolverProperties.getResolutionTimeout().toMillis(), TimeUnit.MILLISECONDS);

            /*
                See tech.units.indriya.quantity.NumberQuantity c'tor.
                Lazy set.
             */
            return adjustedResult = Calculator.of(n).peek();
        }
    }

}
