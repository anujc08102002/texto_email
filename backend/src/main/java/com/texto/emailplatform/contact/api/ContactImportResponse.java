package com.texto.emailplatform.contact.api;
import java.util.List;
public record ContactImportResponse(int accepted,int skipped,List<String> errors){}
