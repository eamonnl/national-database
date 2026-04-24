package com.bmxireland.nationaldb.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads an ability-based division spreadsheet and returns a name → division-code lookup map.
 *
 * <p>The spreadsheet must contain a "Riders" sheet (sheet index 1) with:
 * <ul>
 *   <li>Column 0 — rider name (first and last concatenated)</li>
 *   <li>Column 5 — division code (e.g. D1, D3, M1)</li>
 * </ul>
 * Row 0 is treated as the header row and skipped.</p>
 */
@Service
public class AbilityDivisionLoader {

    private static final Logger log = LoggerFactory.getLogger(AbilityDivisionLoader.class);

    private static final int SHEET_INDEX   = 1;
    private static final int COL_NAME      = 0;
    private static final int COL_DIVISION  = 5;

    private final DatabaseService databaseService;

    public AbilityDivisionLoader(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Reads the division spreadsheet and returns a map of normalized rider name →
     * division code.
     *
     * <p>Names are normalized by stripping all non-alphanumeric characters and
     * converting to uppercase, so "ReubenBYRNE" and "Reuben Byrne" both produce
     * "REUBENBYRNE". Rows with a blank name or division are skipped.</p>
     *
     * @param filePath path to the ability-based division xlsx file
     * @return map of normalized name to division code (e.g. "D3")
     * @throws IOException if the file cannot be read
     */
    public Map<String, String> loadDivisionMap(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Division file not found: " + file.getAbsolutePath());
        }

        log.info("Loading ability-based division file from: {}", file.getAbsolutePath());

        Map<String, String> map = new LinkedHashMap<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            if (wb.getNumberOfSheets() <= SHEET_INDEX) {
                throw new IOException("Division file has no Riders sheet (expected sheet at index " + SHEET_INDEX + ")");
            }

            Sheet sheet = wb.getSheetAt(SHEET_INDEX);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name     = databaseService.getCellStringValue(row.getCell(COL_NAME));
                String division = databaseService.getCellStringValue(row.getCell(COL_DIVISION));

                if (name == null || name.isBlank() || division == null || division.isBlank()) continue;

                String key = normalizeName(name);
                map.put(key, division.trim());
                log.debug("Division map: {} → {}", key, division.trim());
            }
        }

        log.info("Loaded {} riders from division file", map.size());
        return map;
    }

    /** Strips all non-alphanumeric characters and uppercases — used as the map key. */
    static String normalizeName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
