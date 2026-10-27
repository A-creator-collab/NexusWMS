package com.nexuswms.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** In-process notification hub. UI subscribes; services publish (reorder point, order status, approvals, forecast deviation). */
@Service
public class NotificationService {
    public record Note(LocalDateTime at, String level, String message) {}
    private final List<Note> notes = new CopyOnWriteArrayList<>();
    private final List<Consumer<Note>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger unread = new AtomicInteger();

    public void publish(String level, String message) {
        Note n = new Note(LocalDateTime.now(), level, message);
        notes.add(0, n);
        if (notes.size() > 200) notes.remove(notes.size() - 1);
        unread.incrementAndGet();
        for (Consumer<Note> l : listeners) { try { l.accept(n); } catch (RuntimeException ignored) { } }
    }
    public void addListener(Consumer<Note> l) { listeners.add(l); }
    public int unread() { return unread.get(); }
    public void markRead() { unread.set(0); }
    public List<Note> recent() { return List.copyOf(notes); }
}
