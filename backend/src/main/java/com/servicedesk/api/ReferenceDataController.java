package com.servicedesk.api;

import com.servicedesk.api.dto.Views;
import com.servicedesk.application.AdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Services and categories, readable by every signed-in user (for the "report incident" form). */
@RestController
@RequestMapping("/api/v1")
public class ReferenceDataController {

  private final AdminService admin;

  public ReferenceDataController(AdminService admin) {
    this.admin = admin;
  }

  @GetMapping("/services")
  public List<Views.ServiceView> services() {
    return admin.listServices().stream().map(ApiMapper::service).toList();
  }

  @GetMapping("/categories")
  public List<Views.CategoryView> categories() {
    return admin.listCategories().stream().map(ApiMapper::category).toList();
  }
}
