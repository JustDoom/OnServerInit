package com.imjustdoom.pluginsite.controller;

import com.imjustdoom.pluginsite.PluginSiteApplication;
import com.imjustdoom.pluginsite.model.Account;
import com.imjustdoom.pluginsite.model.Resource;
import com.imjustdoom.pluginsite.model.Update;
import com.imjustdoom.pluginsite.repositories.AccountRepository;
import com.imjustdoom.pluginsite.repositories.ResourceRepository;
import com.imjustdoom.pluginsite.util.DateUtil;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

@Controller
@AllArgsConstructor
public class ProfileController {

    private final ResourceRepository resourceRepository;
    private final AccountRepository accountRepository;

    @GetMapping("/profile/{id}")
    public String profile(@RequestParam(name = "sort", required = false, defaultValue = "updated") String sort, @RequestParam(name = "page", required = false, defaultValue = "1") String page, @RequestParam(name = "field", required = false, defaultValue = "") String field, @PathVariable("id") int id, Model model, TimeZone timezone, @CookieValue(value = "username", defaultValue = "") String username, @CookieValue(value = "id", defaultValue = "") String userId) throws SQLException {
        model.addAttribute("username", username);
        model.addAttribute("userId", userId);
        model.addAttribute("page", Integer.parseInt(page));
        ResultSet rs = PluginSiteApplication.getDB().getStmt().executeQuery("SELECT * FROM accounts WHERE id=%s".formatted(id));
        if(!rs.next()) return "resource/404";

        Account account = accountRepository.getById(id);
        //account.setJoined(DateUtil.formatDate(rs.getInt("joined"), timezone));
        account.setId(id);
        account.setUsername(rs.getString("username"));

        switch (field.toLowerCase()) {
            case "resources":
                if (Integer.parseInt(page) < 1) return "redirect:/profile/1?page=1";
                account.setTotalDownloads(0);

                List<Resource> data = new ArrayList<>();
                try {
                    rs = PluginSiteApplication.getDB().getStmt().executeQuery("SELECT COUNT(id) FROM resources WHERE authorid=%s".formatted(id));

                    rs.next();
                    int resources = rs.getInt(1);

                    int startRow = Integer.parseInt(page) * 25 - 25;
                    int endRow = startRow + 25;
                    int total = resources / 25;
                    int remainder = resources % 25;
                    if (remainder > 1) total++;

                    model.addAttribute("total", total);

                    String orderBy = switch (sort) {
                        case "created" -> "ORDER BY creation DESC";
                        case "updated" -> "ORDER BY updated DESC";
                        case "downloads" -> "ORDER BY downloads DESC";
                        case "alphabetical" -> "ORDER BY name ASC";
                        default -> "";
                    };

                    rs = PluginSiteApplication.getDB().getStmt().executeQuery("SELECT * FROM resources WHERE authorid=%s %s LIMIT %s,25".formatted(id, orderBy, startRow));
                    while (rs.next()) {
                        Resource resource = resourceRepository.getById(rs.getInt("id"));
                        //account.setTotalDownloads(account.getTotalDownloads() + resource.getDownloads());
                        data.add(resource);
                    }
                } catch (SQLException e ) {
                    e.printStackTrace();
                }
                model.addAttribute("files", data);
                model.addAttribute("account", account);
                return "profile/resources";
            default:
                model.addAttribute("account", account);
                return "profile/profile";
        }
    }
}