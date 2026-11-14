package com.servicedesk.application;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results plus the total number of matches.
 *
 * @param items the results on this page
 * @param total how many results match in total
 * @param limit page size requested
 * @param offset results skipped before this page
 */
public record Page<T>(List<T> items, long total, int limit, int offset) {

  public Page {
    items = List.copyOf(items);
  }

  public <R> Page<R> map(Function<T, R> f) {
    return new Page<>(items.stream().map(f).toList(), total, limit, offset);
  }
}
