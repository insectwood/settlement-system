package com.settleup.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderStatusTest {

    @Nested
    @DisplayName("Allowed transition")
    class AllowedTransitions {
        @Test
        @DisplayName("CREATED can move to CONFIRMED on payment approval")
        void createdToConfirmed() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CONFIRMED))
                    .isTrue();
        }

        @Test
        @DisplayName("CREATED can move to CANCELLED as compensation for payment failure")
        void createdToCancelled() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED))
                    .isTrue();
        }

        @Test
        @DisplayName("CONFIRMED can move to CANCEL_REQUESTED on customer cancellation")
        void confirmedToCancelRequested() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCEL_REQUESTED))
                    .isTrue();
        }

        @Test
        @DisplayName("CANCEL_REQUESTED can move to CANCELLED once the refund completes")
        void cancelRequestedToCancelled() {
            assertThat(OrderStatus.CANCEL_REQUESTED.canTransitionTo(OrderStatus.CANCELLED))
                    .isTrue();
        }

        @Test
        @DisplayName("CANCEL_REQUESTED falls back to CONFIRMED when the refund fails")
        void cancelRequestedBackToConfirmed() {
            assertThat(OrderStatus.CANCEL_REQUESTED.canTransitionTo(OrderStatus.CONFIRMED))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Rejected transitions")
    class RejectedTransitions {
        @Test
        @DisplayName("CREATED cannot skip ahead to CANCEL_REQUESTED")
        void createdCannotSkipToCancelRequested() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCEL_REQUESTED))
                    .isFalse();
        }

        @Test
        @DisplayName("CONFIRMED cannot go straight to CANCELLED - refund must be confirmed first")
        void confirmedCannotSkipRefund() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED))
                    .isFalse();
        }

        @Test
        @DisplayName("CANCELLED is terminal and allows no further transition")
        void cancelledIsTerminal() {
            assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(OrderStatus.CANCELLED.canTransitionTo(next)).isFalse();
            }
        }

        @Test
        @DisplayName("No status can transition to itself")
        void noSelfTransition() {
            for (OrderStatus status : OrderStatus.values()) {
                assertThat(status.canTransitionTo(status)).isFalse();
            }
        }
    }
}
