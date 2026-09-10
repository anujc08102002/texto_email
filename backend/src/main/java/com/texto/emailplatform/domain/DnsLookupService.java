package com.texto.emailplatform.domain;

import java.util.List;

public interface DnsLookupService {

    List<String> lookupTxt(String name);
}
