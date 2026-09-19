package com.boxy.boxy.core.pdf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders each real PDF template end to end — Thymeleaf fragment resolution and OpenHTMLtoPDF
 * layout are both runtime concerns compilation can't catch (a bad {@code th:replace} fragment
 * signature, or CSS OpenHTMLtoPDF's older engine can't parse, only fail when actually rendered).
 * Wires a bare {@link SpringTemplateEngine} directly — no Spring context / database needed, since
 * {@link PdfDocumentService} only ever touches the engine, never anything web- or JPA-related.
 */
class PdfDocumentServiceTest {

    private final PdfDocumentService service = new PdfDocumentService(templateEngine());

    private static SpringTemplateEngine templateEngine() {
        // Plain ClassLoaderTemplateResolver, not SpringResourceTemplateResolver — the latter needs
        // a live ApplicationContext to resolve `classpath:` resources, which this context-free
        // test deliberately doesn't boot (PdfDocumentService touches nothing Spring-web/JPA).
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Map<String, Object> fakeCompany() {
        Map<String, Object> company = new HashMap<>();
        company.put("name", "Acme Distribuidora");
        company.put("tradeName", "Acme");
        company.put("address", "Av. Central 123");
        company.put("city", "Cochabamba - Bolivia");
        company.put("taxId", "1234567");
        company.put("taxIdLabel", "NIT");
        company.put("phone", "71706123");
        company.put("logoUrl", null);
        company.put("currencySymbol", "Bs.");
        return company;
    }

    private static List<PdfLineItem> fakeLines() {
        return List.of(
                PdfLineItem.builder().sku("GAM103").description("GATA CAIMAN TIPO BAJA 3T")
                        .unitName("PIEZAS").quantity(BigDecimal.valueOf(7)).unitPrice(BigDecimal.valueOf(1100))
                        .discount(BigDecimal.ZERO).subtotal(BigDecimal.valueOf(7700)).build(),
                PdfLineItem.builder().sku("GAM03U").description("GATA CAIMAN C/CAJA 3T")
                        .unitName("PIEZAS").quantity(BigDecimal.valueOf(3)).unitPrice(BigDecimal.valueOf(900))
                        .discount(BigDecimal.ZERO).subtotal(BigDecimal.valueOf(2700)).build());
    }

    @Test
    void rendersTheMoneyDocumentTemplate_asAValidPdf() {
        Map<String, Object> partyFields = new LinkedHashMap<>();
        partyFields.put("Razón Social", "Karina Villalpando");
        partyFields.put("NIT/CI", "00009");

        Map<String, Object> metaFields = new LinkedHashMap<>();
        metaFields.put("Fecha Cotizada", "25-06-2026");
        metaFields.put("Número cotización", "1108");

        Map<String, Object> model = new HashMap<>();
        model.put("company", fakeCompany());
        model.put("docTitle", "COTIZACIÓN");
        model.put("priceColumnLabel", "Precio");
        model.put("total", BigDecimal.valueOf(10400));
        model.put("notes", "");
        model.put("lines", fakeLines());
        model.put("partyFields", partyFields);
        model.put("metaFields", metaFields);
        model.put("amountInWords", AmountInWordsEs.format(BigDecimal.valueOf(10400), "Bs."));
        model.put("sellerName", "LATINATOOLS");
        model.put("leftRoleLabel", "Cotizador");
        model.put("rightRoleLabel", "Vendedor");

        byte[] pdf = service.render("money-document", model);

        assertValidPdf(pdf);
    }

    @Test
    void rendersTheTransferNoteTemplate_asAValidPdf() {
        Map<String, Object> partyFields = new LinkedHashMap<>();
        partyFields.put("Almacén Destino", "Sucursal Norte");

        Map<String, Object> metaFields = new LinkedHashMap<>();
        metaFields.put("Almacén Origen", "Almacén Central");
        metaFields.put("Fecha de Envío", "19-09-2026");

        Map<String, Object> model = new HashMap<>();
        model.put("company", fakeCompany());
        model.put("folio", "TRF-00001");
        model.put("partyFields", partyFields);
        model.put("metaFields", metaFields);
        model.put("notes", "");
        model.put("requestedByName", "Riley Chen");
        model.put("receivedByName", "");
        model.put("lines", List.of(
                Map.of("sku", "SKU-1", "productName", "Widget", "quantitySent", BigDecimal.TEN,
                        "quantityReceived", BigDecimal.TEN)));

        byte[] pdf = service.render("transfer-note", model);

        assertValidPdf(pdf);
    }

    @Test
    void rendersThePurchaseOrderTemplate_asAValidPdf() {
        Map<String, Object> partyFields = new LinkedHashMap<>();
        partyFields.put("Razón Social", "Proveedor S.A.");

        Map<String, Object> metaFields = new LinkedHashMap<>();
        metaFields.put("Fecha de Emisión", "19-09-2026");

        Map<String, Object> model = new HashMap<>();
        model.put("company", fakeCompany());
        model.put("docTitle", "ORDEN DE COMPRA");
        model.put("priceColumnLabel", "Costo");
        model.put("total", BigDecimal.valueOf(500));
        model.put("notes", "");
        model.put("lines", fakeLines());
        model.put("partyFields", partyFields);
        model.put("metaFields", metaFields);
        model.put("amountInWords", AmountInWordsEs.format(BigDecimal.valueOf(500), "Bs."));
        model.put("sellerName", "Riley Chen");
        model.put("leftRoleLabel", "Solicitado por");
        model.put("rightRoleLabel", "Autorizado por");

        byte[] pdf = service.render("money-document", model);

        assertValidPdf(pdf);
    }

    private static void assertValidPdf(byte[] pdf) {
        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(500);
        String header = new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }
}
