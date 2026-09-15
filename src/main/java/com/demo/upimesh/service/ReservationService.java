package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReservationService {

    private final Map<String, PendingReservation> reservations = new ConcurrentHashMap<>();

    @Autowired
    private AccountRepository accounts;

    @Transactional
    public void reserve(String packetId, String senderVpa, BigDecimal amount) {
        Account sender = accounts.findByIdForUpdate(senderVpa)
                .orElseThrow(() -> new IllegalArgumentException("Unknown sender VPA: " + senderVpa));

        BigDecimal available = sender.getAvailableBalance();
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient available balance for sender " + senderVpa
                            + ": available=" + available + ", requested=" + amount);
        }

        sender.setReservedBalance(sender.getReservedBalance().add(amount));
        accounts.save(sender);
        reservations.put(packetId, new PendingReservation(senderVpa, amount));
    }

    @Transactional
    public void release(String packetId) {
        if (packetId == null || packetId.isBlank()) {
            return;
        }

        PendingReservation reservation = reservations.remove(packetId);
        if (reservation == null) {
            return;
        }

        Account sender = accounts.findByIdForUpdate(reservation.senderVpa())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown sender VPA during reservation release: " + reservation.senderVpa()));

        sender.setReservedBalance(sender.getReservedBalance().subtract(reservation.amount()));
        accounts.save(sender);
    }

    @Transactional
    public void clear() {
        reservations.clear();
        for (Account a : accounts.findAll()) {
            a.setReservedBalance(BigDecimal.ZERO);
            accounts.save(a);
        }
    }

    public Optional<PendingReservation> find(String packetId) {
        return Optional.ofNullable(reservations.get(packetId));
    }

    public record PendingReservation(String senderVpa, BigDecimal amount) {
    }
}
