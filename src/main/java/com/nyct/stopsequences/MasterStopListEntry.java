package com.nyct.stopsequences;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Value;

import static com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;

@Value
@JsonNaming(SnakeCaseStrategy.class)
@JsonPropertyOrder({"lineAbbr", "direction", "sequence", "stopId", "stopType", "weekdayTimepoint", "saturdayTimepoint", "sundayTimepoint"})
public class MasterStopListEntry {
    @JsonProperty("lineabbr")
    String lineAbbr;
    String direction;
    long sequence;
    String stopId;
    @JsonProperty("stoptype")
    String stopType;
    int weekdayTimepoint;
    int saturdayTimepoint;
    int sundayTimepoint;
}
