package com.boxy.boxy.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentSummaryTest {

    @Test
    void recognizesChromeOnWindows() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36";
        assertThat(UserAgentSummary.summarize(ua)).isEqualTo("Chrome en Windows");
    }

    @Test
    void recognizesSafariOnMacOs() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/17.0 Safari/605.1.15";
        assertThat(UserAgentSummary.summarize(ua)).isEqualTo("Safari en macOS");
    }

    @Test
    void recognizesSafariOnIos() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/17.0 Mobile/15E148 Safari/604.1";
        assertThat(UserAgentSummary.summarize(ua)).isEqualTo("Safari en iOS");
    }

    @Test
    void recognizesFirefoxOnLinux() {
        String ua = "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";
        assertThat(UserAgentSummary.summarize(ua)).isEqualTo("Firefox en Linux");
    }

    @Test
    void recognizesEdgeOnWindowsRatherThanMisreadingItAsChrome() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
        assertThat(UserAgentSummary.summarize(ua)).isEqualTo("Edge en Windows");
    }

    @Test
    void recognizesChromeOnAndroid() {
        String ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Mobile Safari/537.36";
        assertThat(UserAgentSummary.summarize(ua)).isEqualTo("Chrome en Android");
    }

    @Test
    void fallsBackToAGenericLabelForAnUnrecognizedOrMissingUserAgent() {
        assertThat(UserAgentSummary.summarize(null)).isEqualTo("Dispositivo desconocido");
        assertThat(UserAgentSummary.summarize("")).isEqualTo("Dispositivo desconocido");
        assertThat(UserAgentSummary.summarize("curl/8.0")).isEqualTo("Navegador desconocido en un sistema desconocido");
    }
}
