package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.catalog.entity.Brand;
import com.boxy.boxy.modules.catalog.entity.Category;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.entity.UnitOfMeasure;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POS's product search matches the text against the name, SKU and barcode — and the brand and the
 * category name. Checked against the real schema (rolled back afterwards), because the brand and
 * category joins are the kind of thing that silently drops products when written wrong.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-jwt-signing-secret-do-not-use-in-any-real-environment-1234567890",
        "app.recaptcha.enabled=false"
})
@Transactional
class ProductCatalogSearchTest {

    @Autowired private ProductRepository repository;
    @PersistenceContext private EntityManager em;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Company company;
    private UnitOfMeasure unit;
    private Brand brand;
    private Category category;

    @BeforeEach
    void seed() {
        company = persist(Company.builder().name("Test Co " + tag).taxId("TAX-" + tag).build());
        unit = persist(UnitOfMeasure.builder().company(company).code("U" + tag).name("Unidad").symbol("und").build());
        brand = persist(Brand.builder().company(company).name("Marcazul" + tag).build());
        category = persist(Category.builder().company(company).code("C" + tag).name("Herramientas" + tag).build());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private Product product(String sku, String name, Brand productBrand, Category productCategory) {
        return persist(Product.builder().company(company).unit(unit).sku(sku + tag).name(name)
                .brand(productBrand).category(productCategory).build());
    }

    private java.util.List<String> search(String text) {
        em.flush();
        return repository.searchCatalog(company.getId(), text, null, null, null, null, null, PageRequest.of(0, 20))
                .map(Product::getName).getContent();
    }

    @Test
    void findsAProductByItsBrandName() {
        product("A", "Martillo", brand, category);
        product("B", "Taladro", null, null);

        assertThat(search("marcazul")).containsExactly("Martillo");
    }

    @Test
    void findsAProductByItsCategoryName() {
        product("A", "Martillo", brand, category);
        product("B", "Taladro", null, null);

        assertThat(search("herramientas")).containsExactly("Martillo");
    }

    @Test
    void aProductWithNeitherBrandNorCategoryIsStillFoundByName() {
        product("A", "Martillo", brand, category);
        product("B", "Taladro", null, null);

        assertThat(search("taladro")).containsExactly("Taladro");
    }

    @Test
    void theProductsScreenAndItsPickersSearchTheSameWay() {
        product("A", "Martillo", brand, category);
        product("B", "Taladro", null, null);
        em.flush();

        var byBrand = repository.findAllFiltered(company.getId(), "marcazul", null, null, null, null,
                false, false, false, false, false, PageRequest.of(0, 20)).map(Product::getName).getContent();
        var byCategory = repository.findAllFiltered(company.getId(), "herramientas", null, null, null, null,
                false, false, false, false, false, PageRequest.of(0, 20)).map(Product::getName).getContent();
        var byName = repository.findAllFiltered(company.getId(), "taladro", null, null, null, null,
                false, false, false, false, false, PageRequest.of(0, 20)).map(Product::getName).getContent();

        assertThat(byBrand).containsExactly("Martillo");
        assertThat(byCategory).containsExactly("Martillo");
        assertThat(byName).containsExactly("Taladro");
    }

    @Test
    void withoutTextEverythingActiveComesBack() {
        product("A", "Martillo", brand, category);
        product("B", "Taladro", null, null);

        assertThat(search(null)).containsExactlyInAnyOrder("Martillo", "Taladro");
    }
}
