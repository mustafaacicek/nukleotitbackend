package com.backend.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.backend.backend.model.ChatMessage;
import com.backend.backend.dto.DocumentResponse;
import com.backend.backend.dto.GraphicsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.service.OpenAiService;

@Service
public class OpenAIService {

    @Autowired
    private OpenAiService openAiService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private static final String MODEL = "gpt-4o";
    
    public ChatMessage generateResponse(String userMessage) {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        // System message with Turkish health-focused instructions
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık asistanısın. Kullanıcıların sağlık sorularına kısa, öz ve anlaşılır bir şekilde cevap ver. " +
            "Türkçe tıbbi terimleri kullan ve gerektiğinde basit açıklamalar ekle. " +
            "Verdiğin bilgilerin güncel tıbbi bilgilere dayandığından emin ol. " +
            "Ciddi sağlık sorunları için mutlaka bir doktora başvurulması gerektiğini belirt. " +
            "Yanıtların kısa, net ve Türkçe olmalı. Bilimsel ve doğru bilgiler ver, ancak karmaşık tıbbi jargondan kaçın. " +
            "Kullanıcının sorusuna göre hastalık belirtileri, tedavi yöntemleri, korunma yolları gibi bilgileri içerebilirsin. " +
            "Eğer bir konuda bilgin yoksa veya emin değilsen, bunu dürüstçe belirt."
        ));
        
        // User message
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", userMessage));
        
        // Create completion request
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        // Call OpenAI API
        ChatCompletionChoice choice = openAiService.createChatCompletion(completionRequest).getChoices().get(0);
        
        // Create response message
        return new ChatMessage(
            UUID.randomUUID().toString(),
            choice.getMessage().getContent(),
            "bot",
            LocalDateTime.now()
        );
    }
    
    public DocumentResponse generateDocuments(String disease) {
        try {
            List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
            
            // System message with article retrieval instructions
            messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
                "system",
                "Sen bir sağlık makaleleri asistanısın. Verilen hastalık hakkında en güncel ve doğru bilgileri içeren makaleleri bulmalısın. " +
                "Kullanıcı kısmi bir hastalık adı verdiğinde bile, bu hastalık adını içeren tüm ilgili makaleleri bulmalısın. " +
                "Örneğin, kullanıcı 'et' yazarsa, 'Behçet hastalığı', 'Diyabet' gibi içinde 'et' geçen hastalıklarla ilgili makaleleri bulmalısın. " +
                "Her makale için başlık, kısa açıklama, link ve kaynak bilgisi vermelisin. " +
                "Türkçe karakterlere dikkat etmelisin (ç, ş, ı, ğ, ö, ü). " +
                "Yanıtını JSON formatında vermelisin. " +
                "Linkler gerçek ve güvenilir sağlık kaynaklarına ait olmalı. " +
                "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
                "Yanıtını mutlaka aşağıdaki formatta ver: " +
                "{\"documents\": [{\"title\": \"Makale başlığı\", \"description\": \"Kısa açıklama\", \"link\": \"https://ornek.com/link\", \"source\": \"Kaynak adı\"}]}"
            ));
            
            // User message with disease query
            messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı hakkında makaleler"));
            
            // Create completion request
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .messages(messages)
                .model(MODEL)
                .temperature(0.7)
                .maxTokens(1000)
                .build();
            
            // Call OpenAI API
            ChatCompletionChoice choice = openAiService.createChatCompletion(completionRequest).getChoices().get(0);
            String jsonResponse = choice.getMessage().getContent();
            
            // Clean the response if it contains backticks or other formatting
            jsonResponse = cleanJsonResponse(jsonResponse);
            
            // Parse JSON response
            try {
                // Use a Map to parse the JSON first
                Map<String, List<Map<String, String>>> responseMap = objectMapper.readValue(jsonResponse, 
                    new TypeReference<Map<String, List<Map<String, String>>>>() {});
                
                List<Map<String, String>> docsMap = responseMap.get("documents");
                List<DocumentResponse.Document> documents = new ArrayList<>();
                
                // Convert each map to a Document object
                if (docsMap != null) {
                    for (Map<String, String> docMap : docsMap) {
                        DocumentResponse.Document doc = DocumentResponse.Document.builder()
                            .title(docMap.get("title"))
                            .description(docMap.get("description"))
                            .link(docMap.get("link"))
                            .source(docMap.get("source"))
                            .build();
                        documents.add(doc);
                    }
                }
                
                // Create and return the response
                return DocumentResponse.builder()
                    .success(true)
                    .disease(disease)
                    .documents(documents)
                    .build();
                
            } catch (JsonProcessingException e) {
                return DocumentResponse.builder()
                    .success(false)
                    .disease(disease)
                    .error("JSON parsing error: " + e.getMessage() + "\nResponse was: " + jsonResponse)
                    .build();
            }
            
        } catch (Exception e) {
            return DocumentResponse.builder()
                .success(false)
                .disease(disease)
                .error("Error generating documents: " + e.getMessage())
                .build();
        }
    }
    
    // Helper method to clean JSON response
    private String cleanJsonResponse(String jsonResponse) {
        jsonResponse = jsonResponse.trim();
        if (jsonResponse.startsWith("```json")) {
            jsonResponse = jsonResponse.substring(7);
        } else if (jsonResponse.startsWith("```")) {
            jsonResponse = jsonResponse.substring(3);
        }
        
        if (jsonResponse.endsWith("```")) {
            jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
        }
        
        return jsonResponse.trim();
    }
    
    public GraphicsResponse generateGraphicsData(String disease) {
        try {
            GraphicsResponse response = new GraphicsResponse();
            response.setSuccess(true);
            response.setDisease(disease);
            
            // Her grafik için ayrı ayrı API çağrısı yaparak veri toplama
            try {
                // 1. İlaç üreten ülkeler (Bar Chart)
                List<GraphicsResponse.DrugProducingCountry> drugProducingCountries = fetchDrugProducingCountries(disease);
                response.setDrugProducingCountries(drugProducingCountries);
                
                // 2. İlacın bulunduğu ülkeler (Liste)
                List<String> countriesWithDrug = fetchCountriesWithDrug(disease);
                response.setCountriesWithDrug(countriesWithDrug);
                
                // 3. Yıllık üretim (Line Chart)
                List<GraphicsResponse.YearlyProduction> yearlyProduction = fetchYearlyProduction(disease);
                response.setYearlyProduction(yearlyProduction);
                
                // 4. Ülkelere göre hasta sayısı (Heat Map)
                List<GraphicsResponse.PatientsByCountry> patientsByCountry = fetchPatientsByCountry(disease);
                response.setPatientsByCountry(patientsByCountry);
                
                // 5. Bilim insanları (Tablo)
                List<GraphicsResponse.Scientist> scientists = fetchScientists(disease);
                response.setScientists(scientists);
                
                // 6. Risk faktörleri (Pie Chart)
                List<GraphicsResponse.RiskFactor> riskFactors = fetchRiskFactors(disease);
                response.setRiskFactors(riskFactors);
                
                // 7. Yayılma hızı (Area Chart)
                List<GraphicsResponse.SpreadRate> spreadRate = fetchSpreadRate(disease);
                response.setSpreadRate(spreadRate);
                
                return response;
            } catch (Exception e) {
                response.setSuccess(false);
                response.setError("Veri çekme hatası: " + e.getMessage());
                return response;
            }
            
        } catch (Exception e) {
            GraphicsResponse errorResponse = new GraphicsResponse();
            errorResponse.setSuccess(false);
            errorResponse.setDisease(disease);
            errorResponse.setError("Grafik verileri oluşturma hatası: " + e.getMessage());
            return errorResponse;
        }
    }
    
    // 1. İlaç üreten ülkeler için veri çekme
    private List<GraphicsResponse.DrugProducingCountry> fetchDrugProducingCountries(String disease) throws Exception {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık verileri uzmanısın. Verilen hastalık için ilaç üreten ülkeler ve ürettikleri ilaç sayısı hakkında gerçekçi veriler üretmelisin. " +
            "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
            "Türkçe karakterlere dikkat et (ç, ş, ı, ğ, ö, ü). " +
            "En az 5 ülke verisi üret. " +
            "Yanıtını aşağıdaki formatta ver: " +
            "[{\"country\": \"Ülke adı\", \"drugCount\": sayı}]"
        ));
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı için ilaç üreten ülkeler ve ilaç sayıları"));
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
        String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
        
        return objectMapper.readValue(jsonResponse, new TypeReference<List<GraphicsResponse.DrugProducingCountry>>() {});
    }
    
    // 2. İlacın bulunduğu ülkeler için veri çekme
    private List<String> fetchCountriesWithDrug(String disease) throws Exception {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık verileri uzmanısın. Verilen hastalık için ilacın bulunduğu ülkeler hakkında gerçekçi veriler üretmelisin. " +
            "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
            "Türkçe karakterlere dikkat et (ç, ş, ı, ğ, ö, ü). " +
            "En az 8 ülke verisi üret. " +
            "Yanıtını aşağıdaki formatta ver: " +
            "[\"Ülke1\", \"Ülke2\", \"Ülke3\"]"
        ));
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı ilacının bulunduğu ülkeler"));
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
        String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
        
        return objectMapper.readValue(jsonResponse, new TypeReference<List<String>>() {});
    }
    
    // 3. Yıllık üretim için veri çekme
    private List<GraphicsResponse.YearlyProduction> fetchYearlyProduction(String disease) throws Exception {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık verileri uzmanısın. Verilen hastalık için yıllık ilaç üretim miktarları hakkında gerçekçi veriler üretmelisin. " +
            "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
            "Son 5 yıl için veri üret. " +
            "Yanıtını aşağıdaki formatta ver: " +
            "[{\"year\": \"Yıl\", \"production\": sayı}]"
        ));
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı için yıllık ilaç üretim miktarları"));
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
        String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
        
        return objectMapper.readValue(jsonResponse, new TypeReference<List<GraphicsResponse.YearlyProduction>>() {});
    }
    
    // 4. Ülkelere göre hasta sayısı için veri çekme
    private List<GraphicsResponse.PatientsByCountry> fetchPatientsByCountry(String disease) throws Exception {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık verileri uzmanısın. Verilen hastalık için ülkelere göre hasta sayıları hakkında gerçekçi veriler üretmelisin. " +
            "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
            "Türkçe karakterlere dikkat et (ç, ş, ı, ğ, ö, ü). " +
            "En az 5 ülke verisi üret. " +
            "Yanıtını aşağıdaki formatta ver: " +
            "[{\"country\": \"Ülke adı\", \"patientCount\": sayı}]"
        ));
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı için ülkelere göre hasta sayıları"));
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
        String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
        
        return objectMapper.readValue(jsonResponse, new TypeReference<List<GraphicsResponse.PatientsByCountry>>() {});
    }
    
    // 5. Bilim insanları için veri çekme
    private List<GraphicsResponse.Scientist> fetchScientists(String disease) throws Exception {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık verileri uzmanısın. Verilen hastalık alanında çalışan bilim insanları ve iletişim bilgileri hakkında gerçekçi veriler üretmelisin. " +
            "Her seferinde farklı ve çeşitli bilim insanları üret, tekrar eden isimler kullanma. " +
            "Farklı ülkelerden ve kurumlardan bilim insanları seç. " +
            "E-posta adresleri gerçekçi olmalı ve kurum adreslerini içermeli (ornek.bilimci@universitesi.edu.tr gibi). " +
            "Telefon numaraları uluslararası formatta olmalı (+90 555 123 4567 gibi). " +
            "Türkçe karakterlere dikkat et (ç, ş, ı, ğ, ö, ü). " +
            "Tam olarak 5 bilim insanı verisi üret. " +
            "Yanıtını aşağıdaki formatta ver: " +
            "[{\"name\": \"İsim\", \"institution\": \"Kurum\", \"email\": \"eposta\", \"phone\": \"telefon\", \"country\": \"ülke\"}]"
        ));
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı alanında çalışan bilim insanları ve iletişim bilgileri. Lütfen her seferinde farklı ve çeşitli bilim insanları üret."));
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.9) // Daha yüksek sıcaklık değeri ile daha çeşitli sonuçlar
            .maxTokens(800)
            .build();
        
        ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
        String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
        
        return objectMapper.readValue(jsonResponse, new TypeReference<List<GraphicsResponse.Scientist>>() {});
    }
    
    // 6. Risk faktörleri için veri çekme
    private List<GraphicsResponse.RiskFactor> fetchRiskFactors(String disease) throws Exception {
        List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
            "system",
            "Sen bir sağlık verileri uzmanısın. Verilen hastalık için risk faktörleri ve yüzdeleri hakkında gerçekçi veriler üretmelisin. " +
            "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
            "Türkçe karakterlere dikkat et (ç, ş, ı, ğ, ö, ü). " +
            "En az 5 risk faktörü verisi üret. Yüzdelerin toplamı 100 olmalı. " +
            "Yanıtını aşağıdaki formatta ver: " +
            "[{\"factor\": \"Risk faktörü\", \"percentage\": yüzde}]"
        ));
        
        messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı için risk faktörleri ve yüzdeleri"));
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .model(MODEL)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
        String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
        
        return objectMapper.readValue(jsonResponse, new TypeReference<List<GraphicsResponse.RiskFactor>>() {});
    }
    
    // 7. Yayılma hızı için veri çekme
    private List<GraphicsResponse.SpreadRate> fetchSpreadRate(String disease) throws Exception {
        try {
            List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
            
            messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
                "system",
                "Sen bir sağlık verileri uzmanısın. Verilen hastalık için yayılma hızı ve dönemler hakkında gerçekçi veriler üretmelisin. " +
                "Yanıtını sadece JSON formatında ver, başka açıklama ekleme. " +
                "Son 6 dönem için veri üret (2023 Q1, 2023 Q2, 2023 Q3, 2023 Q4, 2024 Q1, 2024 Q2). " +
                "Tüm değerler 0'dan büyük olmalıdır. Değerler 5 ile 100 arasında olmalıdır. " +
                "Yanıtını aşağıdaki formatta ver: " +
                "[{\"period\": \"Dönem\", \"rate\": sayı}]"
            ));
            
            messages.add(new com.theokanning.openai.completion.chat.ChatMessage("user", disease + " hastalığı için yayılma hızı ve dönemler"));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .messages(messages)
                .model(MODEL)
                .temperature(0.7)
                .maxTokens(500)
                .build();
            
            ChatCompletionChoice choice = openAiService.createChatCompletion(request).getChoices().get(0);
            String jsonResponse = cleanJsonResponse(choice.getMessage().getContent());
            
            List<GraphicsResponse.SpreadRate> spreadRates = objectMapper.readValue(jsonResponse, 
                new TypeReference<List<GraphicsResponse.SpreadRate>>() {});
            
            // Veri doğrulama - eğer herhangi bir değer 0 ise düzelt
            for (GraphicsResponse.SpreadRate rate : spreadRates) {
                if (rate.getRate() <= 0) {
                    // 10-50 arası rastgele bir değer ata
                    rate.setRate(10 + (int)(Math.random() * 40));
                }
            }
            
            return spreadRates;
        } catch (Exception e) {
            // Hata durumunda manuel veri oluştur
            List<GraphicsResponse.SpreadRate> fallbackData = new ArrayList<>();
            String[] periods = {"2023 Q1", "2023 Q2", "2023 Q3", "2023 Q4", "2024 Q1", "2024 Q2"};
            
            for (String period : periods) {
                // 10-50 arası rastgele değerler
                int rate = 10 + (int)(Math.random() * 40);
                fallbackData.add(new GraphicsResponse.SpreadRate(period, rate));
            }
            
            return fallbackData;
        }
    }
}
