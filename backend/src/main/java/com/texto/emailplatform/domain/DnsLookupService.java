package com.texto.emailplatform.domain;

import com.texto.emailplatform.domain.dns.DnsTxtQueryResult;

public interface DnsLookupService {

    DnsTxtQueryResult lookupTxt(String name);
}
