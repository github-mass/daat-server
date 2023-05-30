package com.mass.flightplan.util;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.experimental.Accessors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Data
@Accessors(fluent = false)
public class OpenCaffeineSpec {

    public enum Strength {
        WEAK, SOFT
    }

    private static final String SPLIT_OPTIONS = ",";
    private static final String SPLIT_KEY_VALUE = "=";

    boolean enabled;
    String spec;
    int initialCapacity = -1;
    long maximumWeight = -1L;
    long maximumSize = -1L;
    boolean recordStats;
    @Nullable
    Strength keyStrength;
    @Nullable
    Strength valueStrength;
    @Nullable
    Duration expireAfterWrite;
    @Nullable
    Duration expireAfterAccess;
    @Nullable
    Duration refreshAfterWrite;

    public OpenCaffeineSpec() {
    }

    public Caffeine<Object, Object> toBuilder() {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();

        if(this.spec != null){
            parseSpec(this.spec);
        }

        if (this.initialCapacity != -1) {
            builder.initialCapacity(this.initialCapacity);
        }

        if (this.maximumSize != -1L) {
            builder.maximumSize(this.maximumSize);
        }

        if (this.maximumWeight != -1L) {
            builder.maximumWeight(this.maximumWeight);
        }

        if (this.keyStrength != null) {
            Assert.isTrue(this.keyStrength == Strength.WEAK, "keyStrength must be WEAK");
            builder.weakKeys();
        }

        if (this.valueStrength != null) {
            if (this.valueStrength == Strength.WEAK) {
                builder.weakValues();
            }
            else if (this.valueStrength == Strength.SOFT) {
                builder.softValues();
            }
        }

        if (this.expireAfterWrite != null) {
            builder.expireAfterWrite(this.expireAfterWrite);
        }

        if (this.expireAfterAccess != null) {
            builder.expireAfterAccess(this.expireAfterAccess);
        }

        if (this.refreshAfterWrite != null) {
            builder.refreshAfterWrite(this.refreshAfterWrite);
        }

        if (this.recordStats) {
            builder.recordStats();
        }

        return builder;
    }

    void parseSpec(String specification) {
        String[] var2 = specification.split(",");
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            String option = var2[var4];
            parseOption(option.trim());
        }
    }

    void parseOption(String option) {
        if (!option.isEmpty()) {
            String[] keyAndValue = option.split("=");
            Assert.isTrue(keyAndValue.length <= 2, () -> "key-value pair %s with more than one equals sign".formatted(option));
            String key = keyAndValue[0].trim();
            String value = keyAndValue.length == 1 ? null : keyAndValue[1].trim();
            this.configure(key, value);
        }
    }

    void configure(String key, @Nullable String value) {
        byte var4 = -1;
        switch (key.hashCode()) {
            case -1076762142:
                if (key.equals("expireAfterWrite")) {
                    var4 = 7;
                }
                break;
            case -737229428:
                if (key.equals("weakKeys")) {
                    var4 = 3;
                }
                break;
            case -83937812:
                if (key.equals("softValues")) {
                    var4 = 5;
                }
                break;
            case 336225217:
                if (key.equals("expireAfterAccess")) {
                    var4 = 6;
                }
                break;
            case 502967994:
                if (key.equals("weakValues")) {
                    var4 = 4;
                }
                break;
            case 706249886:
                if (key.equals("refreshAfterWrite")) {
                    var4 = 8;
                }
                break;
            case 817286328:
                if (key.equals("maximumWeight")) {
                    var4 = 2;
                }
                break;
            case 1306358478:
                if (key.equals("recordStats")) {
                    var4 = 9;
                }
                break;
            case 1685649985:
                if (key.equals("maximumSize")) {
                    var4 = 1;
                }
                break;
            case 1725385758:
                if (key.equals("initialCapacity")) {
                    var4 = 0;
                }
        }

        switch (var4) {
            case 0:
                this.initialCapacity(key, value);
                return;
            case 1:
                this.maximumSize(key, value);
                return;
            case 2:
                this.maximumWeight(key, value);
                return;
            case 3:
                this.weakKeys(value);
                return;
            case 4:
                this.valueStrength(key, value, Strength.WEAK);
                return;
            case 5:
                this.valueStrength(key, value, Strength.SOFT);
                return;
            case 6:
                this.expireAfterAccess(key, value);
                return;
            case 7:
                this.expireAfterWrite(key, value);
                return;
            case 8:
                this.refreshAfterWrite(key, value);
                return;
            case 9:
                this.recordStats(value);
                return;
            default:
                throw new IllegalArgumentException("Unknown key " + key);
        }
    }

    void initialCapacity(String key, @Nullable String value) {
        Assert.isTrue(this.initialCapacity == -1, () -> "initial capacity was already set to %,d".formatted(this.initialCapacity));
        this.initialCapacity = parseInt(key, value);
    }

    void maximumSize(String key, @Nullable String value) {
        Assert.isTrue(this.maximumSize == -1L, () -> "maximum size was already set to %,d".formatted(this.maximumSize));
        Assert.isTrue(this.maximumWeight == -1L, () -> "maximum weight was already set to %,d".formatted(this.maximumWeight));
        this.maximumSize = parseLong(key, value);
    }

    void maximumWeight(String key, @Nullable String value) {
        Assert.isTrue(this.maximumWeight == -1L, () -> "maximum weight was already set to %,d".formatted(this.maximumWeight));
        Assert.isTrue(this.maximumSize == -1L, () -> "maximum size was already set to %,d".formatted(this.maximumSize));
        this.maximumWeight = parseLong(key, value);
    }

    void weakKeys(@Nullable String value) {
        Assert.isTrue(value == null, "weak keys does not take a value");
        Assert.isTrue(this.keyStrength == null, "weak keys was already set");
        this.keyStrength = Strength.WEAK;
    }

    void valueStrength(String key, @Nullable String value, Strength strength) {
        Assert.isTrue(value == null, () -> "%s does not take a value".formatted(key));
        Assert.isTrue(this.valueStrength == null, () -> "%s was already set to %s".formatted(key, this.valueStrength));
        this.valueStrength = strength;
    }

    void expireAfterAccess(String key, @Nullable String value) {
        Assert.isTrue(this.expireAfterAccess == null, "expireAfterAccess was already set");
        this.expireAfterAccess = parseDuration(key, value);
    }

    void expireAfterWrite(String key, @Nullable String value) {
        Assert.isTrue(this.expireAfterWrite == null, "expireAfterWrite was already set");
        this.expireAfterWrite = parseDuration(key, value);
    }

    void refreshAfterWrite(String key, @Nullable String value) {
        Assert.isTrue(this.refreshAfterWrite == null, "refreshAfterWrite was already set");
        this.refreshAfterWrite = parseDuration(key, value);
    }

    void recordStats(@Nullable String value) {
        Assert.isTrue(value == null, "record stats does not take a value");
        Assert.isTrue(!this.recordStats, "record stats was already set");
        this.recordStats = true;
    }

    static int parseInt(String key, @Nullable String value) {
        Assert.isTrue(value != null && !value.isEmpty(), () -> "value of key %s was omitted".formatted(key));

        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException var3) {
            throw new IllegalArgumentException(String.format(Locale.US, "key %s value was set to %s, must be an integer", key, value), var3);
        }
    }

    static long parseLong(String key, @Nullable String value) {
        Assert.isTrue(value != null && !value.isEmpty(), () -> "value of key %s was omitted".formatted(key));

        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException var3) {
            throw new IllegalArgumentException(String.format(Locale.US, "key %s value was set to %s, must be a long", key, value), var3);
        }
    }

    static Duration parseDuration(String key, @Nullable String value) {
        Assert.isTrue(value != null && !value.isEmpty(), () -> "value of key %s omitted".formatted(key));
        boolean isIsoFormat = value.contains("p") || value.contains("P");
        if (isIsoFormat) {
            Duration duration = Duration.parse(value);
            Assert.isTrue(!duration.isNegative(), () -> "key %s invalid format; was %s, but the duration cannot be negative".formatted(key, value));
            return duration;
        }
        else {
            long duration = parseLong(key, value.substring(0, value.length() - 1));
            TimeUnit unit = parseTimeUnit(key, value);
            return Duration.ofNanos(unit.toNanos(duration));
        }
    }

    static TimeUnit parseTimeUnit(String key, @Nullable String value) {
        Assert.isTrue(value != null && !value.isEmpty(), () -> "value of key %s omitted".formatted(key));
        char lastChar = Character.toLowerCase(value.charAt(value.length() - 1));
        return switch (lastChar) {
            case 'd' -> TimeUnit.DAYS;
            case 'h' -> TimeUnit.HOURS;
            case 'm' -> TimeUnit.MINUTES;
            case 's' -> TimeUnit.SECONDS;
            default -> throw new IllegalArgumentException(String.format(Locale.US, "key %s invalid format; was %s, must end with one of [dDhHmMsS]", key, value));
        };
    }

}
