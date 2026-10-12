package com.wbscouting.api.controller.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbscouting.api.constant.ContentSectionKey;
import com.wbscouting.api.dto.content.ContactSettingsDto;
import com.wbscouting.api.entity.Admin;
import com.wbscouting.api.entity.ContactChannel;
import com.wbscouting.api.entity.SiteContent;
import com.wbscouting.api.repository.ContactChannelRepository;
import com.wbscouting.api.repository.SiteContentRepository;
import com.wbscouting.api.security.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/institutional/contact", "/admin/institutional/contact"})
@PreAuthorize("hasAnyRole('WEBMASTER', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
public class AdminInstitutionalContactController {

    private final SiteContentRepository siteContentRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Transactional
    public ResponseEntity<ContactSettingsDto> getContactSettings() {
        Optional<SiteContent> contentOpt = siteContentRepository.findBySectionKey("CONTACT_SETTINGS");
        if (contentOpt.isEmpty()) {
            contentOpt = siteContentRepository.findBySectionKey("contact");
        }
        if (contentOpt.isEmpty()) {
            contentOpt = siteContentRepository.findBySectionKey(ContentSectionKey.CONTACT_INFO);
        }

        if (contentOpt.isPresent()) {
            ContactSettingsDto dto = toContactSettingsDto(contentOpt.get());
            if (dto.getPrimaryEmail() != null && !dto.getPrimaryEmail().isBlank()) {
                return ResponseEntity.ok(dto);
            }
        }

        // Se a tabela ou registro estiver vazio, executa upsert com dados padrão/reais
        ContactSettingsDto initialDto = buildInitialOrFromChannels();
        saveToSiteContent(initialDto, null);

        return ResponseEntity.ok(initialDto);
    }

    @PutMapping
    @Transactional
    @AuditAction(action = "UPDATE", resource = "INSTITUTIONAL_CONTENT", description = "Atualização completa de contato institucional")
    public ResponseEntity<ContactSettingsDto> updateContactSettings(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        return mergeAndSave(body, authentication);
    }

    @PatchMapping
    @Transactional
    @AuditAction(action = "UPDATE", resource = "INSTITUTIONAL_CONTENT", description = "Atualização parcial de contato institucional")
    public ResponseEntity<ContactSettingsDto> patchContactSettings(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        return mergeAndSave(body, authentication);
    }

    @PostMapping
    @Transactional
    @AuditAction(action = "CREATE", resource = "INSTITUTIONAL_CONTENT", description = "Criação/Upsert de contato institucional")
    public ResponseEntity<ContactSettingsDto> createOrUpdateContactSettings(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        return mergeAndSave(body, authentication);
    }

    private ResponseEntity<ContactSettingsDto> mergeAndSave(Map<String, Object> body, Authentication authentication) {
        UUID adminId = null;
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Admin admin) {
            adminId = admin.getId();
        }

        Optional<SiteContent> contentOpt = siteContentRepository.findBySectionKey("CONTACT_SETTINGS");
        if (contentOpt.isEmpty()) {
            contentOpt = siteContentRepository.findBySectionKey("contact");
        }

        Map<String, Object> currentMap = new LinkedHashMap<>();
        if (contentOpt.isPresent() && contentOpt.get().getPayloadPt() != null) {
            currentMap.putAll(contentOpt.get().getPayloadPt());
        } else {
            ContactSettingsDto initialDto = buildInitialOrFromChannels();
            try {
                currentMap.putAll(objectMapper.convertValue(initialDto, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("Erro ao converter initialDto para mapa: {}", e.getMessage());
            }
        }

        // Mesclar propriedades de body com currentMap
        if (body != null) {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();

                if ("address".equals(key) && val instanceof Map<?, ?> newAddrMap) {
                    Map<String, Object> currentAddr = getMap(currentMap, "address");
                    currentAddr.putAll(castToMap(newAddrMap));
                    currentMap.put("address", currentAddr);
                } else if ("socialMedia".equals(key) && val instanceof Map<?, ?> newSmMap) {
                    Map<String, Object> currentSm = getMap(currentMap, "socialMedia");
                    currentSm.putAll(castToMap(newSmMap));
                    currentMap.put("socialMedia", currentSm);
                } else {
                    currentMap.put(key, val);
                }
            }
        }

        // Sincronizar alias email / primaryEmail
        if (currentMap.containsKey("email") && !currentMap.containsKey("primaryEmail")) {
            currentMap.put("primaryEmail", currentMap.get("email"));
        } else if (currentMap.containsKey("primaryEmail")) {
            currentMap.put("email", currentMap.get("primaryEmail"));
        }

        ContactSettingsDto updatedDto = convertMapToDto(currentMap);
        saveToSiteContent(updatedDto, adminId);

        return ResponseEntity.ok(updatedDto);
    }

    private void saveToSiteContent(ContactSettingsDto dto, UUID adminId) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.convertValue(dto, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            payload = new LinkedHashMap<>();
            payload.put("primaryEmail", dto.getPrimaryEmail());
            payload.put("email", dto.getPrimaryEmail());
            payload.put("whatsapp", dto.getWhatsapp());
            payload.put("phone", dto.getPhone());
            payload.put("businessHours", dto.getBusinessHours());
            payload.put("whatsappDefaultMessage", dto.getWhatsappDefaultMessage());
            payload.put("socialMediaList", dto.getSocialMediaList());
        }

        // 1. Salvar na chave canônica CONTACT_SETTINGS
        upsertKey("CONTACT_SETTINGS", payload, adminId);

        // 2. Salvar também na chave legada 'contact' para consumo do site público / footer
        upsertKey("contact", payload, adminId);
    }

    private void upsertKey(String sectionKey, Map<String, Object> payload, UUID adminId) {
        SiteContent entity = siteContentRepository.findBySectionKey(sectionKey)
                .orElseGet(() -> SiteContent.builder()
                        .sectionKey(sectionKey)
                        .createdAt(OffsetDateTime.now())
                        .build());

        entity.setPayloadPt(payload);
        entity.setPayloadEn(payload);
        entity.setUpdatedAt(OffsetDateTime.now());
        if (adminId != null) {
            entity.setUpdatedBy(adminId);
        }

        siteContentRepository.saveAndFlush(entity);
    }

    private ContactSettingsDto buildInitialOrFromChannels() {
        List<ContactChannel> channels = contactChannelRepository.findAllByOrderByDisplayOrderAsc();

        String primaryEmail = null;
        String whatsapp = null;
        String phone = null;
        String businessHours = null;
        String addressStreet = null;
        List<ContactSettingsDto.SocialMediaItemDto> socialMediaList = new ArrayList<>();

        if (!channels.isEmpty()) {
            for (ContactChannel ch : channels) {
                String type = ch.getType() != null ? ch.getType().toUpperCase(Locale.ROOT) : "";
                switch (type) {
                    case "EMAIL" -> {
                        if (primaryEmail == null && ch.getValue() != null && !ch.getValue().isBlank()) {
                            primaryEmail = ch.getValue().trim();
                        }
                    }
                    case "WHATSAPP" -> {
                        if (whatsapp == null && ch.getValue() != null && !ch.getValue().isBlank()) {
                            whatsapp = ch.getValue().trim();
                        }
                    }
                    case "PHONE" -> {
                        if (phone == null && ch.getValue() != null && !ch.getValue().isBlank()) {
                            phone = ch.getValue().trim();
                        }
                    }
                    case "OFFICE_HOURS", "BUSINESS_HOURS" -> {
                        if (businessHours == null && ch.getValue() != null && !ch.getValue().isBlank()) {
                            businessHours = ch.getValue().trim();
                        }
                    }
                    case "ADDRESS" -> {
                        if (addressStreet == null && ch.getValue() != null && !ch.getValue().isBlank()) {
                            addressStreet = ch.getValue().trim();
                        }
                    }
                    case "SOCIAL", "INSTAGRAM", "TIKTOK", "YOUTUBE", "LINKEDIN" -> {
                        if (ch.getValue() != null && !ch.getValue().isBlank()) {
                            socialMediaList.add(ContactSettingsDto.SocialMediaItemDto.builder()
                                    .id(ch.getId() != null ? ch.getId().toString() : UUID.randomUUID().toString())
                                    .name(ch.getLabel() != null && !ch.getLabel().isBlank() ? ch.getLabel() : "Instagram")
                                    .url(ch.getValue().trim())
                                    .build());
                        }
                    }
                }
            }
        }

        if (primaryEmail == null || primaryEmail.isBlank()) {
            primaryEmail = "info@wbagency.com.br";
        }
        if (whatsapp == null || whatsapp.isBlank()) {
            whatsapp = "+55 11 97065-6003";
        }
        if (phone == null || phone.isBlank()) {
            phone = "+55 11 97065-6003";
        }
        if (businessHours == null || businessHours.isBlank()) {
            businessHours = "Segunda a Sexta: 09h às 18h (GMT-3)";
        }
        if (socialMediaList.isEmpty()) {
            socialMediaList.add(ContactSettingsDto.SocialMediaItemDto.builder()
                    .id(UUID.randomUUID().toString())
                    .name("Instagram")
                    .url("https://instagram.com/wbagency")
                    .build());
        }

        ContactSettingsDto.AddressDto addressDto = ContactSettingsDto.AddressDto.builder()
                .street(addressStreet != null ? addressStreet : "Avenida Paulista, 1000")
                .number("1000")
                .complement("Cj 1402")
                .neighborhood("Bela Vista")
                .city("São Paulo")
                .state("SP")
                .zipCode("01310-100")
                .country("Brasil")
                .build();

        ContactSettingsDto.SocialMediaDto socialMediaDto = ContactSettingsDto.SocialMediaDto.builder()
                .instagram("https://instagram.com/wbagency")
                .build();

        return ContactSettingsDto.builder()
                .primaryEmail(primaryEmail)
                .email(primaryEmail)
                .scoutingEmail("scouting@wbagency.com.br")
                .pressEmail("press@wbagency.com.br")
                .phone(phone)
                .whatsapp(whatsapp)
                .whatsappDefaultMessage("Olá! Gostaria de mais informações sobre a WB Agency.")
                .businessHours(businessHours)
                .address(addressDto)
                .socialMedia(socialMediaDto)
                .socialMediaList(socialMediaList)
                .build();
    }

    private ContactSettingsDto toContactSettingsDto(SiteContent content) {
        Map<String, Object> pt = content.getPayloadPt() != null ? content.getPayloadPt() : Collections.emptyMap();
        return convertMapToDto(pt);
    }

    private ContactSettingsDto convertMapToDto(Map<String, Object> map) {
        try {
            ContactSettingsDto dto = objectMapper.convertValue(map, ContactSettingsDto.class);
            if (dto.getPrimaryEmail() == null && dto.getEmail() != null) {
                dto.setPrimaryEmail(dto.getEmail());
            }
            if (dto.getEmail() == null && dto.getPrimaryEmail() != null) {
                dto.setEmail(dto.getPrimaryEmail());
            }
            return dto;
        } catch (Exception e) {
            log.warn("Erro ao desserializar ContactSettingsDto do mapa: {}", e.getMessage());
            String email = getString(map, "primaryEmail", getString(map, "email", "info@wbagency.com.br"));
            return ContactSettingsDto.builder()
                    .primaryEmail(email)
                    .email(email)
                    .scoutingEmail(getString(map, "scoutingEmail", "scouting@wbagency.com.br"))
                    .pressEmail(getString(map, "pressEmail", "press@wbagency.com.br"))
                    .phone(getString(map, "phone", "+55 11 97065-6003"))
                    .whatsapp(getString(map, "whatsapp", "+55 11 97065-6003"))
                    .businessHours(getString(map, "businessHours", "Segunda a Sexta: 09h às 18h (GMT-3)"))
                    .whatsappDefaultMessage(getString(map, "whatsappDefaultMessage", ""))
                    .build();
        }
    }

    private static Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val instanceof Map<?, ?> m) {
            return castToMap(m);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> castToMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key)) return defaultValue;
        Object val = map.get(key);
        return (val != null && !val.toString().trim().isEmpty()) ? val.toString().trim() : defaultValue;
    }
}
