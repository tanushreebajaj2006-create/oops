package com.servicedesk.domain.sla;

import java.util.List;

/** <b>Repository</b> of SLA policies (configured by administrators). */
public interface SlaPolicyRepository {

  /** Inserts or replaces the default policy or the policy of its service. */
  SlaPolicy save(SlaPolicy policy);

  List<SlaPolicy> findAll();

  void deleteServicePolicy(String serviceCode);

  /** All stored policies arranged into a catalog ready for the {@link SlaCalculator}. */
  default SlaPolicyCatalog loadCatalog() {
    SlaPolicyCatalog catalog = new SlaPolicyCatalog();
    findAll().forEach(catalog::register);
    return catalog;
  }
}
