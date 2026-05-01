package org.traanite.pubgity

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.scheduling.annotation.EnableScheduling


@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class PubgityApplication {
    @Autowired
    fun setMapKeyDotReplacement(mappingMongoConverter: MappingMongoConverter) {
        mappingMongoConverter.setMapKeyDotReplacement("_")
    }

}

fun main(args: Array<String>) {
    runApplication<PubgityApplication>(*args)
}
