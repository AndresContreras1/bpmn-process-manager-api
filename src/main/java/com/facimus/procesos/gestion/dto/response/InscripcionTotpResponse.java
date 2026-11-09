package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A new authenticator app secret, not active until a code of it is confirmed. It is shown once")
public record InscripcionTotpResponse(
        @Schema(description = "The secret in Base32, to type it into the app by hand",
                example = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP") String secreto,
        @Schema(description = "The otpauth URI for the QR code the app scans",
                example = "otpauth://totp/BPMN%20Process%20Manager:ana%40acme.com?secret=JBSWY3DPEHPK3PXP"
                        + "&issuer=BPMN%20Process%20Manager&algorithm=SHA1&digits=6&period=30") String uri) {
}
