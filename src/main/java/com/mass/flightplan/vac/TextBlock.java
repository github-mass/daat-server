package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.UtilityClass;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;

import static java.lang.Math.*;

@Value
@Builder(toBuilder = true)
public class TextBlock {
    int page;
    @NonNull String text;
    double top, left, right, bottom;

    public double absTop(){
        return page + top;
    }

    public double absBottom(){
        return page + bottom;
    }

    public double width(){
        return right - left;
    }

    public double height(){
        return bottom - top;
    }

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
    public double vRelDist(@NonNull TextBlock other){
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
    public double hRelDist(@NonNull TextBlock other){
        double num =  max(left(), other.left()) - min(right(), other.right());
        double den = max(right(), other.right()) - min(left(), other.left());

        return den == 0 || num == 0 ? 0 : num / den;
    }

    public double hAbsDist(@NonNull TextBlock other){
        return max(left(), other.left()) - min(right(), other.right());
    }

    public double vAbsDist(@NonNull TextBlock other){
        return max(absTop(), other.absTop()) - min(absBottom(), other.absBottom());
    }

    public boolean overlapsVertically(@NonNull TextBlock other){
        return vRelDist(other) < 0;
    }

    public boolean overlapsHorizontally(@NonNull TextBlock other){
        return hRelDist(other) < 0;
    }

    public static TextBlock merge(TextBlock b1, TextBlock b2) {
        return merge(b1, b2, "\t");
    }

    public static TextBlock merge(TextBlock b1, TextBlock b2, String separator) {
        return TextBlock.builder()
                        .page(b1.page())
                        .top(min(b1.top(), b2.top()))
                        .left(min(b1.left(), b2.left()))
                        .bottom(max(b1.bottom(), b2.bottom()))
                        .right(max(b1.right(), b2.right()))
                        .text(b1.left() < b2.left() ? b1.text() + separator + b2.text() : b2.text() + separator + b1.text())
                        .build();
    }

    @UtilityClass
    public static final class Utils {

        @NonNull Predicate<TextBlock> contains(@NonNull String s) {
            return b -> b.text.contains(s);
        }

        @NonNull Predicate<TextBlock> matches(@NonNull Pattern pattern) {
            return b -> pattern.matcher(b.text).matches();
        }

        @NonNull Predicate<TextBlock> find(@NonNull Pattern pattern) {
            return b -> pattern.matcher(b.text).find();
        }

        @NonNull Collector<TextBlock, List<TextBlock>, List<TextBlock>> extractColumn(
            @NonNull TextBlock block, @NonNull BiPredicate<TextBlock, TextBlock> hPredicate, @Nullable BiPredicate<TextBlock, TextBlock> vPredicate
        ) {
            return extractColumn(block, hPredicate, vPredicate, false);
        }

        @NonNull Collector<TextBlock, List<TextBlock>, List<TextBlock>> extractColumn(
            @NonNull TextBlock header, @NonNull BiPredicate<TextBlock, TextBlock> hPredicate, @Nullable BiPredicate<TextBlock, TextBlock> vPredicate, boolean includeHeader
        ) {
            return Collector.of(
                ArrayList::new,
                (list, b) -> {
                    if(hPredicate.test(header, b)) {
                        list.add(b);
                    }
                },
                (l1, l2) -> {l1.addAll(l2); return l1;},
                //finisher: sort, then remove anything that's before the header or further away than vDistMax from the previous element
                list -> {
                    list.sort(Comparator.comparingDouble(TextBlock::absTop));
                    if(!includeHeader){
                        list.remove(header);
                    }

                    TextBlock prev = header, curr;

                    for(int ii = 0; ii < list.size(); ii++){
                        curr = list.get(ii);
                        //remove everything BEFORE base block
                        if(curr.absTop() >= prev.absTop() && (vPredicate == null || vPredicate.test(curr, prev))){
                            prev = list.get(ii);
                        } else {
                            list.set(ii, null);
                        }
                    }
                    return list.stream().filter(Objects::nonNull).toList();
                }
            );
        }

        public static BiPredicate<TextBlock, TextBlock> hRelDistMax(double dist){
            return (b1, b2) -> b1.hRelDist(b2) <= dist;
        }

        public static BiPredicate<TextBlock, TextBlock> hAbsDistMax(double dist){
            return (b1, b2) -> b1.hAbsDist(b2) <= dist;
        }

        public static BiPredicate<TextBlock, TextBlock> vRelDistMax(double dist){
            return (b1, b2) -> b1.vRelDist(b2) <= dist;
        }

        public static BiPredicate<TextBlock, TextBlock> vAbsDistMax(double dist){
            return (b1, b2) -> b1.vAbsDist(b2) <= dist;
        }

        public static Predicate<TextBlock> textIs(@NonNull String s){
            return b -> s.equals(b.text());
        }
    }

    @UtilityClass
    public static class Comp {
        public static final Comparator<TextBlock> TOP_FIRST_THEN_LEFT =
            Comparator.comparingDouble(TextBlock::absTop).thenComparing(TextBlock::left);

        public static Comparator<TextBlock> byRelVDistTo(TextBlock block) {
            return Comparator.comparingDouble(b -> b.vRelDist(block));
        }
    }

    public String boundsToString(){
        return String.format("%03d::[%4f, %4f] -> [%4f, %4f]", page(), top(), left(), bottom(), right());
    }

    public static final Pattern BOUNDS_PATTERN = Pattern.compile("(\\d{3})::\\[([-.0-9]+), ([-.0-9]+)\\] -> \\[([-.0-9]+), ([-.0-9]+)\\]");
    public static TextBlockBuilder parseBounds(CharSequence cs){
        Matcher m = BOUNDS_PATTERN.matcher(cs);

        if(!m.matches()){
            throw new IllegalArgumentException("Not a text block bounds string: " + cs);
        }

        var b = builder();
        String page = m.group(1); int off; for(off = 0; off < page.lastIndexOf('0') && page.charAt(off) == '0'; off++);
        b.page(Integer.parseInt(page, off, page.length(), 10));

        b.top(Double.parseDouble(m.group(2)));
        b.left(Double.parseDouble(m.group(3)));
        b.bottom(Double.parseDouble(m.group(4)));
        b.right(Double.parseDouble(m.group(5)));

        return b;
    }
}
