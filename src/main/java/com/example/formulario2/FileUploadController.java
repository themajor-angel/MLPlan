package com.example.formulario2;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


import com.example.formulario2.storage.StorageFileNotFoundException;
import com.example.formulario2.storage.StorageService;

import static java.util.UUID.randomUUID;

@Controller
public class FileUploadController {

    private final StorageService storageService;

    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/")
    public String listUploadedFiles(Model model) throws IOException {

        model.addAttribute("files", storageService.loadAll().map(
                path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
                        "serveFile", path.getFileName().toString()).build().toUri().toString())
                .collect(Collectors.toList()));

        return "uploadForm";
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }



    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes,
                                   @RequestParam("timeout")  int time) {

        storageService.store(file);
        Resource resource = storageService.loadAsResource(file.getOriginalFilename());

        UUID serialversionUID = randomUUID();
        /*Path donde se guardan los objetos serializados*/
        String path = "C:\\Users\\Mariajosé R\\Downloads\\UploadDocs\\serializedObjects\\";
        String fileName = path + serialversionUID + ".mod";

        try{
            File datasetFile = resource.getFile();
            String pathToFile = datasetFile.getPath();

            File serializedFile = new File(fileName);
            serializedFile.createNewFile();

            createClassifier(pathToFile, time, fileName, serialversionUID);
        }
        catch(Exception e){
            System.out.print(e);
        }
        redirectAttributes.addFlashAttribute("message",
                "You successfully uploaded " + file.getOriginalFilename() + "!");

        return "redirect:/";
    }


    @RequestMapping(value="/predict", method=RequestMethod.POST, params="action=predict")
    public String predict() {
        return "predict";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

    public String createClassifier(String dataFile, int timeout, String fileName, UUID id) throws IOException, InterruptedException {

        /*Path al Jar*/
        File jar = new File("C:\\Users\\MariajoseR\\Downloads\\mlplan-full-builderhelper\\mlplan-full-builderhelper\\cli\\mlplan-cli-0.2.4.jar");
        /*Path  donde se encuentra las configuraciones de log*/
        String configPath = "C:\\Users\\MariajoseR\\Downloads\\UploadDocs\\conf\\log4j.xml";
        String logConfigLevel = "-D"+"\""+"log4j.info"+"\"";
        String logConfigFile = "-D"+"\""+"log4j.configuration=file:"+ configPath + "\"";
        /*Path  donde guarda los mensajes de consola de cada archivo serializado*/
        String pathToLogOutFile = "C:\\Users\\MariajoseR\\Downloads\\UploadDocs\\logs\\" + id + "-OUT.txt";
        String pathToLogErrFile = "C:\\Users\\MariajoseR\\Downloads\\UploadDocs\\logs\\" + id + "-ERROR.txt";
        File logOutputFile = new File(pathToLogOutFile);
        File logErrorFile = new File(pathToLogErrFile);
        String dataFileCom = "\"" + dataFile + "\"";
        String fileNameCom = "\"" + fileName + "\"";
        String jarCom = "\"" + jar.getAbsolutePath() + "\"";

        /*Argumentos para correr el jar desde consola*/
        List<String> args = Arrays.asList("java", logConfigLevel, logConfigFile, "-cp", jarCom, "ai.libs.mlplan.cli.MLPlanCLI", "-f", dataFileCom, "-t", String.valueOf(timeout), "-om", fileNameCom);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectOutput(logOutputFile).redirectError(logErrorFile);
        pb.directory(new File("C:\\Users\\MariajoseR\\Downloads\\UploadDocs"));
        Process p = pb.start();

        /*este ciclo se llama en llama en javascript cliente*/
        while(p.isAlive()){
            System.out.println("Process still running");

            Thread.sleep(1000);

        }
        System.out.println("Process finished");
        // debe retornar la html se pasa el id no lo hace porque se mantiene la conexion abierta
        // programar en javascript
        
        return "/";
    }


}