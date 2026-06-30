package com.microsoft.migration.assets.controller;

import com.microsoft.migration.assets.constants.StorageConstants;
import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/" + StorageConstants.STORAGE_PATH)
@RequiredArgsConstructor
public class S3Controller {

    private final StorageService storageService;

    @GetMapping
    public String listObjects(Model model) {
        List<S3StorageItem> objects = storageService.listObjects();
        model.addAttribute("objects", objects);
        return "list";
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadObject(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/" + StorageConstants.STORAGE_PATH + "/upload";
            }

            storageService.uploadObject(file);
            redirectAttributes.addFlashAttribute("success", "File uploaded successfully");
            return "redirect:/" + StorageConstants.STORAGE_PATH;
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload file: " + e.getMessage());
            return "redirect:/" + StorageConstants.STORAGE_PATH + "/upload";
        }
    }
    
    @GetMapping("/view-page/{key}")
    public String viewObjectPage(@PathVariable String key, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Find the object in the list of objects
            Optional<S3StorageItem> foundObject = storageService.listObjects().stream()
                    .filter(obj -> obj.getKey().equals(key))
                    .findFirst();
            
            if (foundObject.isPresent()) {
                model.addAttribute("object", foundObject.get());
                return "view";
            } else {
                redirectAttributes.addFlashAttribute("error", "Image not found");
                return "redirect:/" + StorageConstants.STORAGE_PATH;
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to view image: " + e.getMessage());
            return "redirect:/" + StorageConstants.STORAGE_PATH;
        }
    }

    private static boolean isValidKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        if (key.contains("..") || key.contains("/") || key.contains("\\")) {
            return false;
        }
        try {
            return !Paths.get(key).isAbsolute();
        } catch (InvalidPathException e) {
            return false;
        }
    }

    @GetMapping("/view/{key}")
    public ResponseEntity<InputStreamResource> viewObject(@PathVariable String key) {
        if (!isValidKey(key)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            InputStream inputStream = storageService.getObject(key);
            
            HttpHeaders headers = new HttpHeaders();
            // Use a generic content type if we don't know the exact type
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/delete/{key}")
    public String deleteObject(@PathVariable String key, RedirectAttributes redirectAttributes) {
        if (!isValidKey(key)) {
            redirectAttributes.addFlashAttribute("error", "Invalid resource identifier");
            return "redirect:/";
        }
        try {
            storageService.deleteObject(key);
            redirectAttributes.addFlashAttribute("success", "File deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete file: " + e.getMessage());
        }
        return "redirect:/" + StorageConstants.STORAGE_PATH;
    }
}