package com.afrisanjaya.shipment.logistics.domain.entity;

import com.afrisanjaya.shipment.logistics.domain.enums.ShipmentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStatusStateMachineTest {

    @ParameterizedTest
    @MethodSource("validTransitions")
    void canTransitionTo_whenTransitionIsValid_thenReturnsTrue(
            ShipmentStatus currentStatus,
            ShipmentStatus targetStatus) {
        Shipment shipment = Shipment.builder().status(currentStatus).build();

        assertThat(shipment.canTransitionTo(targetStatus)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void canTransitionTo_whenTransitionIsInvalid_thenReturnsFalse(
            ShipmentStatus currentStatus,
            ShipmentStatus targetStatus) {
        Shipment shipment = Shipment.builder().status(currentStatus).build();

        assertThat(shipment.canTransitionTo(targetStatus)).isFalse();
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(ShipmentStatus.CREATED, ShipmentStatus.PICKED),
                Arguments.of(ShipmentStatus.PICKED, ShipmentStatus.PACKED),
                Arguments.of(ShipmentStatus.CREATED, ShipmentStatus.CANCELLED),
                Arguments.of(ShipmentStatus.PICKED, ShipmentStatus.CANCELLED),
                Arguments.of(ShipmentStatus.PACKED, ShipmentStatus.DISPATCHED),
                Arguments.of(ShipmentStatus.DISPATCHED, ShipmentStatus.IN_TRANSIT),
                Arguments.of(ShipmentStatus.DISPATCHED, ShipmentStatus.CANCELLED),
                Arguments.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED)
        );
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.concat(
                terminalTransitions(),
                Stream.of(
                        Arguments.of(ShipmentStatus.CREATED, ShipmentStatus.DISPATCHED),
                        Arguments.of(ShipmentStatus.PACKED, ShipmentStatus.IN_TRANSIT),
                        Arguments.of(ShipmentStatus.DISPATCHED, ShipmentStatus.CREATED),
                        Arguments.of(ShipmentStatus.CREATED, ShipmentStatus.CREATED)
                )
        );
    }

    private static Stream<Arguments> terminalTransitions() {
        return Stream.of(ShipmentStatus.values())
                .flatMap(targetStatus -> Stream.of(
                        Arguments.of(ShipmentStatus.DELIVERED, targetStatus),
                        Arguments.of(ShipmentStatus.CANCELLED, targetStatus)
                ));
    }
}
