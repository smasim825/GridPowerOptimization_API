package com.bup.gridwise.service;

import com.bup.gridwise.dto.response.DirectiveInterpretation;
import com.bup.gridwise.model.DirectiveType;
import com.bup.gridwise.model.StructuredAdjustment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmInterpreterService {

    private static final Logger log = LoggerFactory.getLogger(LlmInterpreterService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${LLM_PROVIDER:gemini}")
    private String llmProvider;

    public List<DirectiveInterpretation> interpretNotes(List<String> operatorNotes) {
        return interpretNotes(operatorNotes, null);
    }

    public List<DirectiveInterpretation> interpretNotes(List<String> operatorNotes, com.bup.gridwise.dto.request.BatteryConfig battery) {
        List<DirectiveInterpretation> results = new ArrayList<>();
        if (operatorNotes == null || operatorNotes.isEmpty()) {
            return results;
        }

        boolean hasApiKey = (geminiApiKey != null && !geminiApiKey.isBlank()) ||
                            (openAiApiKey != null && !openAiApiKey.isBlank());

        if (hasApiKey) {
            try {
                results = callLlmApi(operatorNotes, battery);
                if (results != null && results.size() == operatorNotes.size()) {
                    return results;
                }
            } catch (Exception e) {
                log.warn("LLM API call failed or timed out. Falling back to pattern extractor: {}", e.getMessage());
            }
        }

        return fallbackPatternExtraction(operatorNotes, battery);
    }

    private List<DirectiveInterpretation> callLlmApi(List<String> operatorNotes, com.bup.gridwise.dto.request.BatteryConfig battery) throws Exception {
        String prompt = buildPrompt(operatorNotes, battery);

        if ("openai".equalsIgnoreCase(llmProvider) && openAiApiKey != null && !openAiApiKey.isBlank()) {
            return callOpenAiApi(prompt, operatorNotes.size());
        } else if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return callGeminiApi(prompt, operatorNotes.size());
        }

        return fallbackPatternExtraction(operatorNotes, battery);
    }

    private String buildPrompt(List<String> notes, com.bup.gridwise.dto.request.BatteryConfig battery) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert energy management directive extractor.\n");
        sb.append("Extract structured directives from campus operator notes for a 24-hour schedule (hours 0 to 23).\n\n");
        if (battery != null) {
            sb.append("Battery Specifications:\n");
            sb.append(String.format("- capacity_kwh: %.1f\n- initial_energy_kwh: %.1f\n- minimum_energy_kwh: %.1f\n- max_charge_kwh_per_hour: %.1f\n- max_discharge_kwh_per_hour: %.1f\n\n",
                    battery.getCapacity_kwh(), battery.getInitial_energy_kwh(), battery.getMinimum_energy_kwh(),
                    battery.getMax_charge_kwh_per_hour(), battery.getMax_discharge_kwh_per_hour()));
        }
        sb.append("Allowed Directive Types:\n");
        sb.append("1. solar_reduction: {\"hours\": [...], \"factor\": number} (factor is remaining usable solar fraction, e.g. 80% reduction means factor=0.2, 25% usable means factor=0.25, half means factor=0.5)\n");
        sb.append("2. minimum_battery_reserve: {\"hours\": [...], \"minimum_energy_kwh\": number} (If specified as percentage, calculate minimum_energy_kwh = percentage * battery capacity_kwh)\n");
        sb.append("3. no_charge_window: {\"hours\": [...]}\n");
        sb.append("4. no_discharge_window: {\"hours\": [...]}\n");
        sb.append("5. max_grid_window: {\"hours\": [...], \"max_grid_kwh\": number}\n");
        sb.append("6. no_op: applies: false, structured_adjustment: null\n\n");
        sb.append("Time mapping rules:\n");
        sb.append("- Hours are 0..23, start-inclusive, end-exclusive. E.g. noon to 2 PM = [12, 13], 1 PM to 3 PM = [13, 14], 2 PM to 4 PM = [14, 15], 6 PM to 9 PM = [18, 19, 20], 6 PM to 10 PM = [18, 19, 20, 21].\n");
        sb.append("- Irrelevant notes (e.g. cafeteria, library notices, sports registration, seminar moved) must output directive_type: 'no_op', applies: false, structured_adjustment: null.\n\n");
        sb.append("Operator Notes:\n");
        for (int i = 0; i < notes.size(); i++) {
            sb.append("Note ").append(i).append(": \"").append(notes.get(i)).append("\"\n");
        }
        sb.append("\nReturn JSON object with key 'directives' containing an array of objects for each note (in index order):\n");
        sb.append("{\"directives\": [ {\"note_index\": 0, \"applies\": true|false, \"directive_type\": \"...\", \"structured_adjustment\": {...}|null, \"explanation\": \"...\"} ]}\n");
        return sb.toString();
    }

    private List<DirectiveInterpretation> callGeminiApi(String prompt, int noteCount) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                "contents", List.of(java.util.Map.of(
                        "parts", List.of(java.util.Map.of("text", prompt))
                )),
                "generationConfig", java.util.Map.of(
                        "response_mime_type", "application/json"
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            return parseJsonDirectives(text);
        }
        throw new RuntimeException("Gemini API error status " + response.statusCode());
    }

    private List<DirectiveInterpretation> callOpenAiApi(String prompt, int noteCount) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        java.util.Map.of("role", "system", "content", "You output valid JSON objects."),
                        java.util.Map.of("role", "user", "content", prompt)
                ),
                "response_format", java.util.Map.of("type", "json_object")
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("choices").get(0).path("message").path("content").asText();
            return parseJsonDirectives(text);
        }
        throw new RuntimeException("OpenAI API error status " + response.statusCode());
    }

    private List<DirectiveInterpretation> parseJsonDirectives(String jsonText) {
        List<DirectiveInterpretation> list = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(jsonText);
            if (node.isObject() && node.has("directives")) {
                node = node.get("directives");
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    DirectiveInterpretation di = objectMapper.treeToValue(item, DirectiveInterpretation.class);
                    list.add(di);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse LLM JSON output: {}", e.getMessage());
        }
        return list;
    }

    public List<DirectiveInterpretation> fallbackPatternExtraction(List<String> operatorNotes) {
        return fallbackPatternExtraction(operatorNotes, null);
    }

    public List<DirectiveInterpretation> fallbackPatternExtraction(List<String> operatorNotes, com.bup.gridwise.dto.request.BatteryConfig battery) {
        List<DirectiveInterpretation> list = new ArrayList<>();

        for (int i = 0; i < operatorNotes.size(); i++) {
            String note = operatorNotes.get(i).trim();
            DirectiveInterpretation di = extractFromPattern(i, note, battery);
            list.add(di);
        }
        return list;
    }

    private DirectiveInterpretation extractFromPattern(int index, String note, com.bup.gridwise.dto.request.BatteryConfig battery) {
        String lower = note.toLowerCase();

        // Check for distractor / non-actionable notes
        if (isDistractorNote(lower)) {
            return new DirectiveInterpretation(index, false, DirectiveType.no_op, null, "This note does not affect today's energy schedule.");
        }

        // 1. Solar Reduction
        if (lower.contains("solar") || lower.contains("pv") || lower.contains("panel")) {
            List<Integer> hrs = parseHoursFromText(lower);
            if (!hrs.isEmpty()) {
                double factor = parseFactorFromText(lower);
                StructuredAdjustment adj = new StructuredAdjustment(hrs, factor, null, null);
                return new DirectiveInterpretation(index, true, DirectiveType.solar_reduction, adj, "Extracted solar reduction.");
            }
        }

        // 2. Minimum Battery Reserve
        if (lower.contains("reserve") || lower.contains("keep at least") || lower.contains("minimum battery") || lower.contains("remain in the battery") || lower.contains("hold at least") || lower.contains("battery capacity stored")) {
            List<Integer> hrs = parseHoursFromText(lower);
            double reqMin = parseReserveFromText(lower, battery);
            if (!hrs.isEmpty() && reqMin > 0) {
                StructuredAdjustment adj = new StructuredAdjustment(hrs, null, reqMin, null);
                return new DirectiveInterpretation(index, true, DirectiveType.minimum_battery_reserve, adj, "Extracted minimum battery reserve.");
            }
        }

        // 3. No Charge Window
        if ((lower.contains("do not charge") || lower.contains("no charge") || lower.contains("charger will be isolated") || lower.contains("charging circuit") || lower.contains("charging is disabled") || lower.contains("disallow charge") || lower.contains("stop charging"))
            && !lower.contains("discharge")) {
            List<Integer> hrs = parseHoursFromText(lower);
            if (!hrs.isEmpty()) {
                StructuredAdjustment adj = new StructuredAdjustment(hrs, null, null, null);
                return new DirectiveInterpretation(index, true, DirectiveType.no_charge_window, adj, "Extracted no charge window.");
            }
        }

        // 4. No Discharge Window
        if (lower.contains("do not discharge") || lower.contains("no discharge") || lower.contains("must not discharge") || lower.contains("stop discharging") || lower.contains("discharging is prohibited") || lower.contains("discharging is disabled")) {
            List<Integer> hrs = parseHoursFromText(lower);
            if (!hrs.isEmpty()) {
                StructuredAdjustment adj = new StructuredAdjustment(hrs, null, null, null);
                return new DirectiveInterpretation(index, true, DirectiveType.no_discharge_window, adj, "Extracted no discharge window.");
            }
        }

        // 5. Max Grid Window
        if (lower.contains("grid import") || lower.contains("grid intake") || lower.contains("max grid") || lower.contains("grid limit") || lower.contains("grid cap") || lower.contains("transformer limit") || lower.contains("must not exceed") || lower.contains("at or below") || lower.contains("stay at or below") || lower.contains("cap grid")) {
            List<Integer> hrs = parseHoursFromText(lower);
            double maxGrid = parseMaxGridFromText(lower);
            if (!hrs.isEmpty() && maxGrid > 0) {
                StructuredAdjustment adj = new StructuredAdjustment(hrs, null, null, maxGrid);
                return new DirectiveInterpretation(index, true, DirectiveType.max_grid_window, adj, "Extracted max grid window limit.");
            }
        }

        // 6. Default Distractor -> no_op
        return new DirectiveInterpretation(index, false, DirectiveType.no_op, null, "This note does not affect today's energy schedule.");
    }

    private boolean isDistractorNote(String text) {
        if (text.contains("solar") || text.contains("pv") || text.contains("panel") ||
            text.contains("battery") || text.contains("reserve") || text.contains("charg") ||
            text.contains("discharg") || text.contains("grid") || text.contains("kwh") ||
            text.contains("transformer") || text.contains("feeder") || text.contains("inverter") ||
            text.contains("substation")) {
            return false;
        }

        return true;
    }

    private List<Integer> parseHoursFromText(String text) {
        List<Integer> hrs = new ArrayList<>();

        String normalized = text.replaceAll("\\bnoon\\b", "12 pm")
                                .replaceAll("\\bmidnight\\b", "12 am");

        // 1. Match 24-hour ranges like "14:00 to 16:00" or "14-16"
        Pattern p24 = Pattern.compile("(?:from|between)?\\s*(\\d{1,2}):00\\s*(?:until|to|-)\\s*(\\d{1,2}):00", Pattern.CASE_INSENSITIVE);
        Matcher m24 = p24.matcher(normalized);
        if (m24.find()) {
            int start = Integer.parseInt(m24.group(1));
            int end = Integer.parseInt(m24.group(2));
            for (int h = start; h < end; h++) {
                if (h >= 0 && h <= 23) hrs.add(h);
            }
            return hrs;
        }

        // 2. Match 12-hour ranges "X AM/PM until/to Y AM/PM" or "X to Y PM"
        Pattern p1 = Pattern.compile("(?:from|between)?\\s*(\\d{1,2})(?::\\d{2})?\\s*(am|pm)?\\s*(?:until|to|and|-)\\s*(\\d{1,2})(?::\\d{2})?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(normalized);
        if (m1.find()) {
            int start = Integer.parseInt(m1.group(1));
            String startAmpm = m1.group(2);
            int end = Integer.parseInt(m1.group(3));
            String endAmpm = m1.group(4);

            if (endAmpm == null) endAmpm = startAmpm;
            if ("pm".equalsIgnoreCase(endAmpm) && end < 12) end += 12;
            if ("am".equalsIgnoreCase(endAmpm) && end == 12) end = 0;

            if ("pm".equalsIgnoreCase(startAmpm) && start < 12) start += 12;
            if ("am".equalsIgnoreCase(startAmpm) && start == 12) start = 0;

            if (startAmpm == null && endAmpm != null) {
                if (start < 12 && "pm".equalsIgnoreCase(endAmpm) && start <= end - 12) {
                    start += 12;
                }
            }

            for (int h = start; h < end; h++) {
                if (h >= 0 && h <= 23) {
                    hrs.add(h);
                }
            }
        }
        return hrs;
    }

    private double parseFactorFromText(String text) {
        if (text.contains("half")) {
            return 0.5;
        }
        if (text.contains("one-fifth")) {
            return 0.2;
        }

        Pattern pDrop = Pattern.compile("(?:drop|fall|down|treated as|leave|to about|to)?\\s*(\\d+)%", Pattern.CASE_INSENSITIVE);
        Matcher mDrop = pDrop.matcher(text);
        if (mDrop.find()) {
            double val = Double.parseDouble(mDrop.group(1));
            if (text.contains("reduction") || text.contains("reduced") || text.contains("cut") || text.contains("drop by") || text.contains("fall by")) {
                return Math.max(0.0, (100.0 - val) / 100.0);
            } else {
                return val / 100.0;
            }
        }

        return 0.2;
    }

    private double parseReserveFromText(String text, com.bup.gridwise.dto.request.BatteryConfig battery) {
        Pattern pPct = Pattern.compile("(\\d+)%\\s*(?:of\\s*(?:the)?\\s*battery\\s*capacity)?", Pattern.CASE_INSENSITIVE);
        Matcher mPct = pPct.matcher(text);
        if (mPct.find()) {
            double pct = Double.parseDouble(mPct.group(1)) / 100.0;
            if (battery != null && battery.getCapacity_kwh() > 0) {
                return pct * battery.getCapacity_kwh();
            }
            return pct * 200.0; // standard default fallback
        }

        Pattern p = Pattern.compile("(?:at least|minimum|keep|requires|hold|reserve)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:kwh)?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }

        return 100.0;
    }

    private double parseMaxGridFromText(String text) {
        Pattern p = Pattern.compile("(?:exceed|below|intake|limit is|at or below|cap of|cap at)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:kwh)?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }

        return 150.0;
    }
}
