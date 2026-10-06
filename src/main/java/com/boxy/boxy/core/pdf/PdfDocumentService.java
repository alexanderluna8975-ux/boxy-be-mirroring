package com.boxy.boxy.core.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders a printable document (Cotización, Nota de Venta, Nota de Transferencia, Orden de
 * Compra, Recepción) to real PDF bytes — every one of them a Thymeleaf template under
 * {@code templates/pdf/} styled to the same shared look (see {@code pdf/fragments/document.html}),
 * rendered via OpenHTMLtoPDF.
 * <p>
 * Thymeleaf produces well-formed HTML5, not strict XHTML, so it's parsed tolerantly through Jsoup
 * (see {@link W3CDom}) rather than fed straight to OpenHTMLtoPDF's XML parser — a stray `&` or an
 * unclosed tag in real business data (a product name, say) would otherwise break the whole render.
 */
@Service
@RequiredArgsConstructor
public class PdfDocumentService {

    private final SpringTemplateEngine templateEngine;

    /** Only host these PDF templates are ever allowed to fetch an image from — `company.logoUrl`
     *  (validated on write too, see {@code CompanyService}) is the only user-suppliable URL that
     *  reaches this renderer, and its only legitimate source is a Cloudinary-hosted upload. Without
     *  this, a `logoUrl` pointed at an internal address (e.g. a cloud metadata endpoint) would have
     *  this server itself make that request the moment the PDF renders — a classic SSRF-via-image. */
    private static final Set<String> ALLOWED_EXTERNAL_IMAGE_HOSTS = Set.of("res.cloudinary.com");

    /** {@code templateName} is the file under {@code templates/pdf/}, without the {@code .html}
     *  suffix — e.g. {@code "quotation"} for {@code templates/pdf/quotation.html}. */
    public byte[] render(String templateName, Map<String, Object> model) {
        // Locale.US, not es-BO: every piece of Spanish TEXT in these templates is written out
        // literally (labels, and dates/amounts are pre-formatted in Java before reaching the
        // model) — the only thing the Context locale actually touches is Thymeleaf's
        // #numbers.formatDecimal(...), and the reference document these templates match uses a
        // plain period for the decimal point (`1100.00`), not the Spanish-locale comma.
        Context context = new Context(Locale.US);
        context.setVariables(model);
        String html = templateEngine.process("pdf/" + templateName, context);

        org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
        jsoupDocument.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        Document w3cDocument = new W3CDom().fromJsoup(jsoupDocument);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUriResolver(this::resolveAllowedImageUri);
            builder.withW3cDocument(w3cDocument, "");
            builder.toStream(output);
            builder.run();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF for template '" + templateName + "'", e);
        }
        return output.toByteArray();
    }

    /** Refuses to resolve (returns {@code null}, which OpenHTMLtoPDF treats as "skip this image"
     *  rather than an error) any URI whose host isn't on {@link #ALLOWED_EXTERNAL_IMAGE_HOSTS}. */
    private String resolveAllowedImageUri(String baseUri, String uri) {
        try {
            String host = URI.create(uri).getHost();
            if (host != null && ALLOWED_EXTERNAL_IMAGE_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) {
            // malformed URI — fall through to refusing it below
        }
        return null;
    }
}
