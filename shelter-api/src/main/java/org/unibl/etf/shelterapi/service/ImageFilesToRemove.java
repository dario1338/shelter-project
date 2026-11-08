package org.unibl.etf.shelterapi.service;

import java.util.List;

/** Published when photos have been removed from the database; their files are deleted after the commit. */
public record ImageFilesToRemove(List<String> storageKeys) {
}
