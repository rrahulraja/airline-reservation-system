package com.airline.booking.flight.dto;

import java.util.List;

public record SeatMapRow(int row, List<SeatStatusView> seats) {
}
