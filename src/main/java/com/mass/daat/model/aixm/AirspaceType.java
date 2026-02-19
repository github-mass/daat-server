package com.mass.daat.model.aixm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum AirspaceType {

    CTR("CTR", "CTR", null, null),
    PROHIBITED("P", "P", null, null),
    RESTRICTED("R", "R", null, null),
    DANGEROUS("D", "D", null, null),
    NATURAL_RESERVE("OTHER/PRN", "D-OTHER", "PRN", "PRN"),
    NO_LOW_OVERFLIGHT("OTHER/SUR", "D-OTHER", "SUR", "SUR"),

    /* WARNING: haven't double-checked the AIXM codes on the following (using french SIA only) */
    PARACHUTING_ZONE("OTHER/PJE", "D-OTHER", "PJE", "Pje"),
    RECREATIONAL_MODEL_AIRCRAFT_FLIGHT("OTHER/AER", "D-OTHER", "AER", "Aer"),
    RECREATIONAL_WINCH_ASSISTED_GLIDER_LAUNCH("OTHER/TRPLA", "D-OTHER", "TrPla", "TrPla"),
    RECREATIONAL_WINCH_ASSISTED_GLIDER_AND_FREE_FLIGHT_LAUNCH("OTHER/TRPVL", "D-OTHER", "TrPVL", "TrPVL"),
    RECREATIONAL_WINCH_ASSISTED_FREE_FLIGHT_LAUNCH("OTHER/TRVL", "D-OTHER", "TrVL", "TrVL"),
    RECREATIONAL_AEROBATICS("OTHER/VOL", "D-OTHER", "VOL", "Vol"),
    ;

    private final @NonNull @Getter String code;
    private final @NonNull @Getter(AccessLevel.PACKAGE) String aixmTypeCode;
    private final @Nullable @Getter(AccessLevel.PACKAGE) String aixmLocalType;
    private final @Nullable @Getter(AccessLevel.PACKAGE) String siaTypeEspace;

    AirspaceType(@NotNull String code, @NotNull String aixmTypeCode, @Nullable String aixmLocalType, @Nullable String siaTypeEspace) {
        this.code = code;
        this.aixmTypeCode = aixmTypeCode;
        this.aixmLocalType = aixmLocalType;
        this.siaTypeEspace = siaTypeEspace;
    }

    public static Optional<AirspaceType> aixmParse(@NonNull String aixmCode, @Nullable String localType){
        return Optional.of(new Key(aixmCode, localType)).map(LOOKUP::get);
    }

    private record Key(String mainCode, String subCode) {}

    private static final Map<Key, AirspaceType> LOOKUP;
    static {
        LOOKUP = Arrays.stream(AirspaceType.values())
            .collect(Collectors.toUnmodifiableMap(
                t -> new Key(t.aixmTypeCode, t.aixmLocalType),
                Function.identity()
            ));
    }

}
