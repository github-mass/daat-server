package com.mass.daat;

import com.giffing.bucket4j.spring.boot.starter.config.cache.SyncCacheResolver;
import com.giffing.bucket4j.spring.boot.starter.context.ConsumptionProbeHolder;
import com.mass.daat.util.OpenCaffeineSpec;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class CacheConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "cache")
    public Map<String, OpenCaffeineSpec> cacheConfig() {
        return new HashMap<>();
    }

    private final Set<String> BUCKET4JCACHES = Set.of("rate-limit-buckets");

    @Bean(autowireCandidate = false)
    @SuppressWarnings({"unchecked, rawtypes"})
    Map<String, CaffeineProxyManager<?>> bucket4jProxyManagers() {
        Map<String, OpenCaffeineSpec> specs = cacheConfig();

        return BUCKET4JCACHES.stream()
                             .filter(specs::containsKey)
                             .collect(Collectors.toMap(Function.identity(),
                                 name -> {
                                     var ret = new CaffeineProxyManager(specs.get(name).toBuilder(), Duration.ofMinutes(30));
                                     log.info("Using cache for '{}': {}", "rate-limit-buckets", ret.getCache());
                                     return ret;
                                 }
                             ));
    }

    @Bean
    public CacheManager customCacheManager() {
        List<Cache> caches = new ArrayList<>();

        cacheConfig().forEach(
            (key, spec) -> {
                if (!BUCKET4JCACHES.contains(key)) {
                    log.info("Creating cache '{}' with config: {}", key, spec);
                    var cache = new CaffeineCache(key, spec.toBuilder().build());
                    caches.add(cache);
                }
            }
        );

        bucket4jProxyManagers().forEach(
            (key, man) -> {
                //noinspection rawtypes
                com.github.benmanes.caffeine.cache.Cache cache = man.getCache();
                //noinspection unchecked
                caches.add(new CaffeineCache(key, cache));
            }
        );

        var ret = new SimpleCacheManager();
        ret.setCaches(caches);

        return ret;
    }

    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SyncCacheResolver bucket4JCacheResolver() {
        return cacheName -> {
            CaffeineProxyManager cpm = bucket4jProxyManagers().get(cacheName);

            Assert.notNull(cpm, () -> "Could not find bucket4j cache '%s'".formatted(cacheName));

            return (key, numTokens, bucketConfiguration, metricsListener) -> {
                Bucket bucket = cpm.builder().build(key, bucketConfiguration).toListenable(metricsListener);
                return new ConsumptionProbeHolder(bucket.tryConsumeAndReturnRemaining(numTokens));
            };
        };
    }

}
