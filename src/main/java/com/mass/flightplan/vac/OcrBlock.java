package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;

import static java.lang.Math.*;

@Value
@Builder(toBuilder = true)
public class OcrBlock {
    int page;
    @NonNull String text;
    double top, left, right, bottom;

    public double absTop(){
        return page + top;
    }

    public double absBottom(){
        return page + bottom;
    }

    public static final Comparator<OcrBlock> TOP_FIRST_THEN_LEFT =
        Comparator.comparingDouble(OcrBlock::absTop).thenComparing(OcrBlock::left);

    /**
     * Vertical distance between two blocks. Ranges from -1 to +1.
     * <p> Negative values mean the blocks overlap vertically,
     * and the value indicates the proportion of overlap in relation to sum of the blocks' height,
     * with -1 meaning complete overlap.</p>
     * <p>
     * Positive values mean the blocks don't overlap, and the value indicates the relationship
     * of the distance between the blocks' nearest edges to that between their furthest edges.
     * </p>
     * <p> Zero means the blocks touch without overlapping. </p>
     *
     * @param other the other block.
     * @return the vertical distance between the two blocks
     */
    public double vDist(@NonNull OcrBlock other){
        double num =  max(absTop(), other.absTop()) - min(absBottom(), other.absBottom());
        double den = max(absBottom(), other.absBottom()) - min(absTop(), other.absTop());

        return den == 0 || num == 0 ? 0 : num / den;
    }

    /**
     * Horizontal distance between two blocks. Ranges from -1 to positive N.
     * <p> Negative values mean the blocks overlap horizontally,
     * and the value indicates the proportion of overlap in relation to sum of the blocks' width,
     * with -1 meaning complete overlap.</p>
     * <p>
     * Positive values mean the blocks don't overlap, and the value indicates the relationship
     * of the distance between the blocks' nearest edges to that between their furthest edges.
     * </p>
     * <p> Zero means the blocks touch without overlapping. </p>
     *
     * @param other the other block.
     * @return the horizontal distance between the two blocks
     */
    public double hDist(@NonNull OcrBlock other){
        double num =  max(left(), other.left()) - min(right(), other.right());
        double den = max(right(), other.right()) - min(left(), other.left());

        return den == 0 || num == 0 ? 0 : num / den;
    }

    public boolean overlapsVertically(@NonNull OcrBlock other){
        return vDist(other) < 0;
    }

    public boolean overlapsHorizontally(@NonNull OcrBlock other){
        return hDist(other) < 0;
    }

    @UtilityClass
    public static final class Utils {

        @NonNull Predicate<OcrBlock> contains(@NonNull String s) {
            return b -> b.text.contains(s);
        }

        @NonNull Predicate<OcrBlock> matches(@NonNull Pattern pattern) {
            return b -> pattern.matcher(b.text).matches();
        }

        @NonNull Predicate<OcrBlock> find(@NonNull Pattern pattern) {
            return b -> pattern.matcher(b.text).find();
        }

        @NonNull Collector<OcrBlock, List<OcrBlock>, List<OcrBlock>> extractColumn(
            @NonNull OcrBlock block, double hTol, double vMaxInterval
        ) {
            return extractColumn(block, hTol, vMaxInterval, false);
        }

        @NonNull Collector<OcrBlock, List<OcrBlock>, List<OcrBlock>> extractColumn(
            @NonNull OcrBlock block, double horizontalTolerance, double vMaxInterval, boolean includeHeader
        ) {
            return Collector.of(
                ArrayList::new,
                (list, b) -> {
                    if(
                        (horizontalTolerance < 0 || abs(b.left() - block.left()) <= horizontalTolerance)
                        &&
                            (horizontalTolerance < 0 || abs(b.right() - block.right()) <= horizontalTolerance)
                    ) {
                        list.add(b);
                    }
                },
                (l1, l2) -> {l1.addAll(l2); return l1;},
                //finisher: sort and remove anything over vMaxInterval
                list -> {
                    list.sort(Comparator.comparingDouble(OcrBlock::absTop));
                    if(!includeHeader){
                        list.remove(block);
                    }

                    OcrBlock prev = block, curr;

                    for(int ii = 0; ii < list.size(); ii++){
                        curr = list.get(ii);
                        //remove everything BEFORE base block
                        double delta = curr.absTop() - prev.absTop();
                        if(delta >= 0 && delta < vMaxInterval){
                            prev = list.get(ii);
                        } else {
                            list.set(ii, null);
                        }
                    }
                    return list.stream().filter(Objects::nonNull).toList();
                }
            );
        }
    }
}
