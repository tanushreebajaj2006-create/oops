package com.servicedesk.domain.incident;

import com.servicedesk.domain.exception.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * <b>Repository</b> of incident categories. Because categories are data rather than an enum, an
 * administrator adds one with a single {@link #save} - no code change, no redeploy.
 */
public interface CategoryRepository {

  Category save(Category category);

  Optional<Category> find(String code);

  /**
   * @throws EntityNotFoundException if there is no such category
   */
  default Category get(String code) {
    return find(code)
        .orElseThrow(() -> new EntityNotFoundException("Category " + code + " not found"));
  }

  /** All categories, ordered by name. */
  List<Category> findAll();
}
