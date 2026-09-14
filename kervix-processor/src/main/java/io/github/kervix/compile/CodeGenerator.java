package io.github.kervix.compile;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Generates Java Mapper implementations from a validated compilation model.
 * 
 * @author kervix
 * @since 2024/10/01
 */
interface CodeGenerator {
    
    /**
     * Generates a complete Mapper implementation.
     * 
     * @param mapperInterface Mapper interface
     * @param compilationModel validated Mapper compilation model
     * @param elementUtils javac element utilities
     * @param typeUtils javac type utilities
     * @return generated Java source
     * @throws GenerationException when source generation fails
     */
    String generateMapperImpl(TypeElement mapperInterface, MapperCompilationModel compilationModel,
                             Elements elementUtils, Types typeUtils) throws GenerationException;
    
    /**
     * Generates one Mapper method implementation.
     * 
     * @param methodModel validated method model
     * @return generated method source
     * @throws GenerationException when source generation fails
     */
    String generateMethodImpl(MapperCompilationModel.MethodModel methodModel) throws GenerationException;
    
    /**
     * Source generation failure.
     */
    class GenerationException extends Exception {
        public GenerationException(String message) {
            super(message);
        }
        
        public GenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
