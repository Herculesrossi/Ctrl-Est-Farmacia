package farmacia.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppConstants {

    public static final java.time.format.DateTimeFormatter DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final String DEFAULT_JSON = "estoque_farmacia.json";
    public static final String LEGACY_CSV = "estoque_farmacia.csv";

    public static final int MAX_NOME_LENGTH = 200;
    public static final int MAX_LOTE_LENGTH = 50;
    public static final int MAX_FORNECEDOR_LENGTH = 100;

    private AppConstants() {}

    public static Path jsonPath() {
        return Paths.get(System.getProperty("user.dir")).resolve(DEFAULT_JSON);
    }

    public static Path legacyCsvPath() {
        return Paths.get(System.getProperty("user.dir")).resolve(LEGACY_CSV);
    }

    public static Path jsonTempPath() {
        return jsonPath().resolveSibling(DEFAULT_JSON + ".tmp");
    }
}
