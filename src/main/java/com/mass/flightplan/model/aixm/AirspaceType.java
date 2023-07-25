package com.mass.flightplan.model.aixm;

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

    CTR("CTR", "CTR", null),
    PROHIBITED("P", "P", null),
    RESTRICTED("R", "R", null),
    DANGEROUS("D", "D", null),
    PARACHUTING_ZONE("OTHER/PJE", "D-OTHER", "PJE"),
    NATURAL_RESERVE("OTHER/PRN", "D-OTHER", "PRN")

    ;

    private final @NonNull @Getter String code;
    private final @NonNull @Getter(AccessLevel.PACKAGE) String aixmTypeCode;
    private final @Nullable @Getter(AccessLevel.PACKAGE) String aixmLocalType;

    AirspaceType(@NotNull String code, @NotNull String aixmTypeCode, @Nullable String aixmLocalType) {
        this.code = code;
        this.aixmTypeCode = aixmTypeCode;
        this.aixmLocalType = aixmLocalType;
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
