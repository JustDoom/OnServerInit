package com.imjustdoom.pluginsite.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imjustdoom.pluginsite.PluginSiteApplication;
import com.imjustdoom.pluginsite.dtos.in.CreateResourceRequest;
import com.imjustdoom.pluginsite.dtos.in.CreateUpdateRequest;
import com.imjustdoom.pluginsite.dtos.out.SimpleResourceDto;
import com.imjustdoom.pluginsite.model.Account;
import com.imjustdoom.pluginsite.model.Resource;
import com.imjustdoom.pluginsite.model.Update;
import com.imjustdoom.pluginsite.repositories.AccountRepository;
import com.imjustdoom.pluginsite.repositories.ResourceRepository;
import com.imjustdoom.pluginsite.repositories.UpdateRepository;
import com.imjustdoom.pluginsite.service.LogoService;
import com.imjustdoom.pluginsite.util.FileUtil;
import lombok.AllArgsConstructor;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Controller
@AllArgsConstructor
public class ResourcesController {

    private final LogoService logoService;
    private final ResourceRepository resourceRepository;
    private final AccountRepository accountRepository;
    private final UpdateRepository updateRepository;

    @GetMapping("/resources")
    public String resources(Account account, @RequestParam(name = "search", required = false) String search, @RequestParam(name = "sort", required = false, defaultValue = "updated") String sort, @RequestParam(name = "page", required = false, defaultValue = "1") String page, Model model) throws SQLException {

        if (Integer.parseInt(page) < 1) return "redirect:/resources?page=1";

        List<SimpleResourceDto> data = new ArrayList<>();
        List<String> searchList = new ArrayList<>();
        int resources, total, remainder;

        if (search != null && !search.equals("")) {
            List<BoundExtractedResult<Resource>> searchResults;
            searchResults = FuzzySearch.extractSorted(search, resourceRepository.findAll(), Resource::getName);
            for (BoundExtractedResult<Resource> extractedResult : searchResults) {
                if (extractedResult.getScore() < 30) continue;
                searchList.add(extractedResult.getString());

                Optional<Resource> optionalResource = resourceRepository.findByNameEqualsIgnoreCase(extractedResult.getString());
                Resource resource = optionalResource.get();

                Integer downloads = updateRepository.getTotalDownloads(resource.getId());
                data.add(SimpleResourceDto.create(resource, downloads == null ? 0 : downloads));

            }

            resources = searchList.size();
            total = resources / 25;
            remainder = resources % 25;
            if (remainder > 1) total++;
        } else {

            total = resourceRepository.findAll().size() / 25;
            remainder = resourceRepository.findAll().size() % 25;
            if (remainder > 1) total++;

            Sort sort1 = Sort.by(sort).ascending();
            Pageable pageable = PageRequest.of(Integer.parseInt(page) - 1, 25, sort1);

            for (Resource resource : resourceRepository.findAll(pageable)) {
                Integer downloads = updateRepository.getTotalDownloads(resource.getId());
                data.add(SimpleResourceDto.create(resource, downloads == null ? 0 : downloads));
            }
        }

        model.addAttribute("total", total);
        model.addAttribute("files", data);
        model.addAttribute("account", account);
        model.addAttribute("page", Integer.parseInt(page));

        return "resource/resources";
    }

    @GetMapping("/resources/{id_s}")
    public String resource(Account account, @RequestParam(name = "sort", required = false, defaultValue = "uploaded") String sort, @PathVariable("id_s") String id_s, @RequestParam(name = "field", required = false, defaultValue = "") String field, Model model) throws SQLException, MalformedURLException {
        int id = 0;
        try{
            id = Integer.parseInt(id_s);
        }catch (NumberFormatException e){
            return "error/404";
        }
        Optional<Resource> optionalResource = resourceRepository.findById(id);

        if (optionalResource.isEmpty()) return "error/404";

        Resource resource = resourceRepository.getById(id);

        String description = resource.getDescription();

        description.replaceAll("script", "error style=\"display:none;\"");
        Parser parser = Parser.builder().build();
        Node document = parser.parse(description);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String html = renderer.render(document);

        resource.setDescription(html);

        model.addAttribute("account", account);
        model.addAttribute("resource", resource);
        model.addAttribute("editUrl", "/resources/%s/edit".formatted(id));
        model.addAttribute("uploadUrl", "/resources/%s/upload/".formatted(id));

        Integer totalDownloads = updateRepository.getTotalDownloads(resource.getId());
        model.addAttribute("totalDownloads", totalDownloads == null ? 0 : totalDownloads);

        switch (field.toLowerCase()) {
            case "updates":
                Sort sort1 = Sort.by(sort).descending();

                // TODO: improve getting the versions and software. 100% not the best way to do this
                List<Update> data = updateRepository.findAllByResourceId(id, sort1);
                List<List<String>> versions = new ArrayList<>();
                List<String> versionLists = new ArrayList<>();
                for(Update update : data) {
                    JsonObject jsonObject = JsonParser.parseString(update.getVersions()).getAsJsonObject();
                    List<String> versionList = new ArrayList<>();

                    versionList.add(jsonObject.get("versions").getAsJsonArray().get(0).getAsString());
                    boolean first = true;
                    StringBuilder versionString = new StringBuilder();
                    String splitter = "";
                    for(JsonElement v:jsonObject.get("versions").getAsJsonArray()) {
                        if(first) {
                            first = false;
                            continue;
                        }
                        versionString.append(splitter);
                        splitter = ", ";
                        versionString.append(v.getAsString());
                    }
                    versionLists.add(versionString.toString());
                    versions.add(versionList);
                }

                model.addAttribute("versions", versions);
                model.addAttribute("versionLists", versionLists);
                model.addAttribute("updates", data);
                return "resource/updates";
            default:
                return "resource/resource";
        }
    }

    @GetMapping("/resources/{id}/edit/update/{fileId}")
    public String editResourceUpdate(@PathVariable("id") int id, @PathVariable("fileId") int fileId, Model model, Account account) {

        Optional<Resource> optionalResource = resourceRepository.findById(id);
        if (optionalResource.isEmpty()) return "error/404";

        Optional<Update> optionalUpdate = updateRepository.findById(fileId);
        if (optionalUpdate.isEmpty()) return "error/404";
        Update update = optionalUpdate.get();

        model.addAttribute("update", update);
        model.addAttribute("account", account);

        return "resource/editUpdate";
    }

    @PostMapping("/resources/{id}/edit/update/{fileId}")
    public String editUpdateSubmit(@ModelAttribute Update update, @PathVariable("id") int id) {

        System.out.println(update.getDescription());
        //TODO: fix description not updating

        updateRepository.setInfo(update.getId(), update.getName(), update.getDescription(), update.getVersion());

        return "redirect:/resources/%s".formatted(id);
    }

    @GetMapping("/resources/{id}/edit")
    public String editResource(@RequestParam(name = "error", required = false) String error, @PathVariable("id") int id, Model model, Account account) {
        model.addAttribute("error", error);
        model.addAttribute("maxUploadSize", PluginSiteApplication.config.getMaxUploadSize());

        Optional<Resource> optionalResource = resourceRepository.findById(id);
        Resource resource = optionalResource.get();

        if (optionalResource.isEmpty()) return "error/404";

        model.addAttribute("authorid", resource.getAuthor());
        model.addAttribute("resource", resource);
        model.addAttribute("url", "/resources/edit/" + id);
        model.addAttribute("account", account);

        return "resource/edit";
    }

    @PostMapping("/resources/{id}/edit")
    public String editSubmit(@RequestParam("logo") MultipartFile file, @ModelAttribute Resource resource, @PathVariable("id") int id) throws IOException {

        if (!file.isEmpty()) {
            if (!file.getContentType().contains("image")) {
                return "redirect:/resources/edit/%s/?error=logotype".formatted(resource.getId());
            }

            if (file.getSize() > 10000) {
                return "redirect:/resources/edit/%s/?error=filesize".formatted(resource.getId());
            }

            BufferedImage image = ImageIO.read(file.getInputStream());

            if (image.getHeight() != image.getWidth())
                return "redirect:/resources/edit/%s/?error=logosize".formatted(resource.getId());

            Path destinationFile = Path.of("./resources/logos/%s/logo.png".formatted(id)).normalize().toAbsolutePath();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        resourceRepository.setInfo(resource.getId(), resource.getName(), resource.getBlurb(), resource.getDescription(),
                resource.getDonation(), resource.getSource(), resource.getSupport());

        return "redirect:/resources/%s".formatted(resource.getId());
    }

    //TODO: Do sanity checks
    @PostMapping("/resources/create")
    public RedirectView createSubmit(@ModelAttribute CreateResourceRequest resourceRequest, Account account) throws IOException {

        Resource resource = new Resource(resourceRequest.getName(), resourceRequest.getDescription(),
                resourceRequest.getBlurb(), resourceRequest.getDonationLink(), resourceRequest.getSourceCodeLink(),
                "", account, resourceRequest.getSupportLink());

        resourceRepository.save(resource);

        int id = resource.getId();

        if (!FileUtil.doesFileExist("./resources/logos/" + id)) {
            try {
                Files.createDirectory(Paths.get("./resources/logos/" + id));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!FileUtil.doesFileExist("./resources/logos/" + id + "/logo.png")) {
            InputStream stream = PluginSiteApplication.class.getResourceAsStream("/pictures/logo.png");
            assert stream != null;
            Files.copy(stream, Path.of("./resources/logos/" + id + "/logo.png"));
        }

        return new RedirectView("/resources/" + id);
    }

    @GetMapping("/resources/create")
    public String create(Model model, Account account) {
        model.addAttribute("account", account);
        model.addAttribute("resource", new CreateResourceRequest());
        return "resource/create";
    }

    @GetMapping("/resources/{id}/upload")
    public String uploadFile(@RequestParam(name = "error", required = false) String error, @PathVariable("id") int id, Model model, Account account) {

        Optional<Resource> optionalResource = resourceRepository.findById(id);
        Resource resource = optionalResource.get();

        if (optionalResource.isEmpty()) return "error/404";

        model.addAttribute("resource", resource);
        model.addAttribute("update", new CreateUpdateRequest());
        model.addAttribute("url", "/resources/%s/upload/".formatted(id));
        model.addAttribute("error", error);
        model.addAttribute("maxUploadSize", PluginSiteApplication.config.getMaxUploadSize());
        model.addAttribute("account", account);

        return "resource/upload";
    }

    @PostMapping("/resources/{id}/upload")
    public String uploadFilePost(@RequestParam(name = "softwareCheckbox") List<String> softwareBoxes, @RequestParam(name = "versionCheckbox") List<String> versionBoxes, @PathVariable("id") int id, @RequestParam("file") MultipartFile file, @ModelAttribute CreateUpdateRequest updateRequest) throws IOException, SQLException {

        if (file.isEmpty() && updateRequest.getExternalLink() == null) {
            //return "redirect:/resources/upload/" + updateRequest.getId() + "/?error=filesize";
        }
        if (!file.getOriginalFilename().endsWith(".jar") && updateRequest.getExternalLink() == null) {
            //return "redirect:/resources/upload/" + updateRequest.getId() + "/?error=filetype";
        }

        JsonObject versions = new JsonObject();
        JsonArray versionsArray = new JsonArray();
        for(String s : versionBoxes) versionsArray.add(s);
        versions.add("versions", versionsArray);

        JsonObject software = new JsonObject();
        JsonArray softwareArray = new JsonArray();
        for(String s : softwareBoxes) softwareArray.add(s);
        software.add("versions", softwareArray);

        Update update = new Update(updateRequest.getDescription(), file.getOriginalFilename(), updateRequest.getVersion(), "", updateRequest.getName(), versions, software);

        update.setResource(resourceRepository.getById(id));
        updateRepository.save(update);

        if (updateRequest.getExternalLink() == null || updateRequest.getExternalLink().equals("")) {
            if (!FileUtil.doesFileExist("./resources/plugins/" + update.getId())) {
                try {
                    Files.createDirectory(Paths.get("./resources/plugins/" + update.getId()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Path destinationFile = Path.of("./resources/plugins/" + update.getId() + "/" + file.getOriginalFilename()).normalize().toAbsolutePath();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        String download = "%s/files/%s/download/%s".formatted(PluginSiteApplication.config.domain, update.getResource().getId(), update.getId());
        resourceRepository.setDownload(id, download);
        updateRepository.setDownload(update.getId(), download);

        return "redirect:/resources/%s".formatted(id);
    }
}