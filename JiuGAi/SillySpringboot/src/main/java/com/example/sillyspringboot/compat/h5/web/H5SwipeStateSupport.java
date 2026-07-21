package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class H5SwipeStateSupport {

    private H5SwipeStateSupport() {
    }

    static SwipeState build(AppMessage current, AppMessageMapper messageMapper) {
        if (current == null) {
            return new SwipeState(List.of(""), 0, List.of(0));
        }
        String content = current.getContent() == null ? "" : current.getContent();
        String ref = current.getStMessageRef();
        if (ref == null || ref.isBlank()) {
            return new SwipeState(List.of(content), 0, List.of(0));
        }

        List<AppMessage> rows = new ArrayList<>(messageMapper.listByStMessageRef(ref));
        rows.sort((a, b) -> {
            int ai = a == null || a.getSwipeIndex() == null ? 0 : a.getSwipeIndex();
            int bi = b == null || b.getSwipeIndex() == null ? 0 : b.getSwipeIndex();
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
            long aid = a == null || a.getId() == null ? 0L : a.getId();
            long bid = b == null || b.getId() == null ? 0L : b.getId();
            return Long.compare(aid, bid);
        });

        LinkedHashMap<Integer, String> byIndex = new LinkedHashMap<>();
        long currentId = current.getId() == null ? 0L : current.getId();
        for (AppMessage row : rows) {
            if (!isSwipeVisible(row)) {
                continue;
            }
            int idx = row.getSwipeIndex() == null ? 0 : row.getSwipeIndex();
            String rowContent = row.getContent() == null ? "" : row.getContent();
            long rowId = row.getId() == null ? 0L : row.getId();
            if (rowId == currentId) {
                byIndex.put(idx, rowContent);
            } else {
                byIndex.putIfAbsent(idx, rowContent);
            }
        }

        int activeIndex = current.getSwipeIndex() == null ? 0 : current.getSwipeIndex();
        byIndex.put(activeIndex, content);

        if (byIndex.isEmpty()) {
            return new SwipeState(List.of(content), 0, List.of(0));
        }

        List<Integer> orderedIndexes = new ArrayList<>(byIndex.keySet());
        orderedIndexes.sort(Integer::compareTo);

        List<String> swipes = orderedIndexes.stream().map(byIndex::get).toList();
        int swipeIndex = orderedIndexes.indexOf(activeIndex);
        if (swipeIndex < 0) {
            swipeIndex = 0;
        }
        return new SwipeState(swipes, swipeIndex, orderedIndexes);
    }

    private static boolean isSwipeVisible(AppMessage row) {
        if (row == null || row.getContent() == null || row.getContent().isBlank()) {
            return false;
        }
        String status = row.getStatus() == null ? "" : row.getStatus();
        return "SUCCESS".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status);
    }

    record SwipeState(List<String> swipes, int swipeIndex, List<Integer> logicalIndexes) {
    }
}
