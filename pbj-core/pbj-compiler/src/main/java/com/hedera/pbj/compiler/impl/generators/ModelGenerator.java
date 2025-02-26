package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.Common.camelToUpperSnake;
import static com.hedera.pbj.compiler.impl.Common.cleanDocStr;
import static com.hedera.pbj.compiler.impl.Common.getFieldsHashCode;
import static com.hedera.pbj.compiler.impl.Common.getJavaFile;
import static com.hedera.pbj.compiler.impl.Common.javaPrimitiveToObjectType;
import static com.hedera.pbj.compiler.impl.generators.EnumGenerator.EnumValue;
import static com.hedera.pbj.compiler.impl.generators.EnumGenerator.createEnum;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.Field.FieldType;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Code generator that parses protobuf files and generates nice Java source for record files for each message type and
 * enum.
 */
@SuppressWarnings({"StringConcatenationInLoop", "EscapedSpace"})
public final class ModelGenerator implements Generator {

	private static final String HASH_CODE_MANIPULATION =
		"""
		// Shifts: 30, 27, 16, 20, 5, 18, 10, 24, 30
		hashCode += hashCode << 30;
		hashCode ^= hashCode >>> 27;
		hashCode += hashCode << 16;
		hashCode ^= hashCode >>> 20;
		hashCode += hashCode << 5;
		hashCode ^= hashCode >>> 18;
		hashCode += hashCode << 10;
		hashCode ^= hashCode >>> 24;
		hashCode += hashCode << 30;
		""".indent(DEFAULT_INDENT);

	/**
	 * {@inheritDoc}
	 *
	 * <p>Generates a new model object, as a Java Record type.
	 */
	public void generate(final Protobuf3Parser.MessageDefContext msgDef,
						 final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {

		// The javaRecordName will be something like "AccountID".
		final var javaRecordName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		// The modelPackage is the Java package to put the model class into.
		final String modelPackage = lookupHelper.getPackageForMessage(FileType.MODEL, msgDef);
		// The File to write the sources that we generate into
		final File javaFile = getJavaFile(destinationSrcDir, modelPackage, javaRecordName);
		// The javadoc comment to use for the model class, which comes **directly** from the protobuf schema,
		// but is cleaned up and formatted for use in JavaDoc.
		String javaDocComment = (msgDef.docComment()== null) ? "" :
				cleanDocStr(msgDef.docComment().getText().replaceAll("\n \\*\s*\n","\n * <p>\n"));
		// The Javadoc "@Deprecated" tag, which is set if the protobuf schema says the field is deprecated
		String deprecated = "";
		// The list of fields, as defined in the protobuf schema
		final List<Field> fields = new ArrayList<>();
		// The generated Java code for an enum field if OneOf is used
		final List<String> oneofEnums = new ArrayList<>();
		// The generated Java code for getters if OneOf is used
		final List<String> oneofGetters = new ArrayList<>();
		// The generated Java code for has methods for normal fields
		final List<String> hasMethods = new ArrayList<>();
		// The generated Java import statements. We'll build this up as we go.
		final Set<String> imports = new TreeSet<>();
		imports.add("com.hedera.pbj.runtime");
		imports.add("com.hedera.pbj.runtime.io");
		imports.add("com.hedera.pbj.runtime.io.buffer");
		imports.add("com.hedera.pbj.runtime.io.stream");
		imports.add("edu.umd.cs.findbugs.annotations");

		// Iterate over all the items in the protobuf schema
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var oneOfField = new OneOfField(item.oneof(), javaRecordName, lookupHelper);
				final var enumName = oneOfField.nameCamelFirstUpper() + "OneOfType";
				final int maxIndex = oneOfField.fields().get(oneOfField.fields().size() - 1).fieldNumber();
				final Map<Integer, EnumValue> enumValues = new HashMap<>();
				for (final var field : oneOfField.fields()) {
					final String javaFieldType = javaPrimitiveToObjectType(field.javaFieldType());
					final String enumComment = cleanDocStr(field.comment())
						.replaceAll("[\t\s]*/\\*\\*","") // remove doc start indenting
						.replaceAll("\n[\t\s]+\\*","\n") // remove doc indenting
						.replaceAll("/\\*\\*","") //  remove doc start
						.replaceAll("\\*\\*/",""); //  remove doc end
					enumValues.put(field.fieldNumber(), new EnumValue(field.name(), field.deprecated(), enumComment));
					// generate getters for one ofs
					oneofGetters.add("""
							/**
							 * Direct typed getter for one of field $fieldName.
							 *
							 * @return one of value or null if one of is not set or a different one of value
							 */
							public @Nullable $javaFieldType $fieldName() {
								return $oneOfField.kind() == $enumName.$enumValue ? ($javaFieldType)$oneOfField.value() : null;
							}
							
							/**
							 * Convenience method to check if the $oneOfField has a one-of with type $enumValue
							 *
							 * @return true of the one of kind is $enumValue
							 */
							public boolean has$fieldNameUpperFirst() {
							    return $oneOfField.kind() == $enumName.$enumValue;
							}
							
							/**
							 * Gets the value for $fieldName if it has a value, or else returns the default
							 * value for the type.
							 *
							 * @param defaultValue the default value to return if $fieldName is null
							 * @return the value for $fieldName if it has a value, or else returns the default value
							 */
							public $javaFieldType $fieldNameOrElse(@NonNull final $javaFieldType defaultValue) {
							    return has$fieldNameUpperFirst() ? $fieldName() : defaultValue;
							}
							
							/**
							 * Gets the value for $fieldName if it was set, or throws a NullPointerException if it was not set.
							 *
							 * @return the value for $fieldName if it has a value
							 * @throws NullPointerException if $fieldName is null
							 */
							public @NonNull $javaFieldType $fieldNameOrThrow() {
							    return requireNonNull($fieldName(), "Field $fieldName is null");
							}
							"""
							.replace("$fieldNameUpperFirst",field.nameCamelFirstUpper())
							.replace("$fieldName",field.nameCamelFirstLower())
							.replace("$javaFieldType",javaFieldType)
							.replace("$oneOfField",oneOfField.nameCamelFirstLower())
							.replace("$enumName",enumName)
							.replace("$enumValue",camelToUpperSnake(field.name()))
							.indent(DEFAULT_INDENT)
					);
					if (field.type() == Field.FieldType.MESSAGE) {
						field.addAllNeededImports(imports, true, false, false);
					}
				}
				final String enumComment = """
									/**
									 * Enum for the type of "%s" oneof value
									 */""".formatted(oneOfField.name());
				final String enumString = createEnum(enumComment ,"",enumName,maxIndex,enumValues, true)
						.indent(DEFAULT_INDENT * 2);
				oneofEnums.add(enumString);
				fields.add(oneOfField);
				imports.add("com.hedera.pbj.runtime");
			} else if (item.mapField() != null) { // process map fields
				System.err.println("Encountered a mapField that was not handled in " + javaRecordName);
			} else if (item.field() != null && item.field().fieldName() != null) {
				final SingleField field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, false, false);
				if (field.type() == FieldType.MESSAGE) {
					hasMethods.add("""
							/**
							 * Convenience method to check if the $fieldName has a value
							 *
							 * @return true of the $fieldName has a value
							 */
							public boolean has$fieldNameUpperFirst() {
							    return $fieldName != null;
							}
							
							/**
							 * Gets the value for $fieldName if it has a value, or else returns the default
							 * value for the type.
							 *
							 * @param defaultValue the default value to return if $fieldName is null
							 * @return the value for $fieldName if it has a value, or else returns the default value
							 */
							public $javaFieldType $fieldNameOrElse(@NonNull final $javaFieldType defaultValue) {
							    return has$fieldNameUpperFirst() ? $fieldName : defaultValue;
							}
							
							/**
							 * Gets the value for $fieldName if it has a value, or else throws an NPE.
							 * value for the type.
							 *
							 * @return the value for $fieldName if it has a value
							 * @throws NullPointerException if $fieldName is null
							 */
							public @NonNull $javaFieldType $fieldNameOrThrow() {
							    return requireNonNull($fieldName, "Field $fieldName is null");
							}
							
							/**
							 * Executes the supplied {@link Consumer} if, and only if, the $fieldName has a value
							 *
							 * @param ifPresent the {@link Consumer} to execute
							 */
							public void if$fieldNameUpperFirst(@NonNull final Consumer<$javaFieldType> ifPresent) {
							    if (has$fieldNameUpperFirst()) {
							        ifPresent.accept($fieldName);
							    }
							}
							"""
							.replace("$fieldNameUpperFirst", field.nameCamelFirstUpper())
							.replace("$javaFieldType", field.javaFieldType())
							.replace("$fieldName", field.nameCamelFirstLower())
							.indent(DEFAULT_INDENT)
					);
				}
			} else if (item.optionStatement() != null){
				if ("deprecated".equals(item.optionStatement().optionName().getText())) {
					deprecated = "@Deprecated ";
				} else {
					System.err.println("Unhandled Option: "+item.optionStatement().getText());
				}
			} else if (item.reserved() == null){ // ignore reserved and warn about anything else
				System.err.println("ModelGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}

		// process field java doc and insert into record java doc
		if (!fields.isEmpty()) {
			String recordJavaDoc = !javaDocComment.isEmpty() ?
					javaDocComment.replaceAll("\n\s*\\*/","") :
					"/**\n * "+javaRecordName;
			recordJavaDoc += "\n *";
			for(var field: fields) {
				recordJavaDoc += "\n * @param "+field.nameCamelFirstLower()+" "+
							field.comment()
								.replaceAll("\n", "\n *         "+" ".repeat(field.nameCamelFirstLower().length()));
			}
			recordJavaDoc += "\n */";
			javaDocComment = cleanDocStr(recordJavaDoc);
		}

		// === Build Body Content
		String bodyContent = "";

		// static codec and default instance
		bodyContent +=
       			"""
                /** Protobuf codec for reading and writing in protobuf format */
                public static final Codec<$modelClass> PROTOBUF = new $qualifiedCodecClass();
                /** JSON codec for reading and writing in JSON format */
                public static final JsonCodec<$modelClass> JSON = new $qualifiedJsonCodecClass();
				
                /** Default instance with all fields set to default values */
                public static final $modelClass DEFAULT = newBuilder().build();
                """
				.replace("$modelClass",javaRecordName)
				.replace("$qualifiedCodecClass",lookupHelper.getFullyQualifiedMessageClassname(FileType.CODEC, msgDef))
				.replace("$qualifiedJsonCodecClass",lookupHelper.getFullyQualifiedMessageClassname(FileType.JSON_CODEC, msgDef))
				.indent(DEFAULT_INDENT);

		// constructor
		if (fields.stream().anyMatch(f -> f instanceof OneOfField || f.optionalValueType())) {
			bodyContent += """
     
					/**
					 * Override the default constructor adding input validation
					 * %s
					 */
					public %s {
					%s
					}
					"""
					.formatted(
						fields.stream().map(field -> "\n * @param "+field.nameCamelFirstLower()+" "+
								field.comment()
								.replaceAll("\n", "\n *         "+" ".repeat(field.nameCamelFirstLower().length()))
						).collect(Collectors.joining()),
						javaRecordName,
						fields.stream()
								.filter(f -> f instanceof OneOfField)
								.map(ModelGenerator::generateConstructorCode)
								.collect(Collectors.joining("\n"))
					)
					.indent(DEFAULT_INDENT);
		}

		// Generate a call to private method that iterates through fields and calculates the hashcode
		final String statements = getFieldsHashCode(fields, "");

		bodyContent +=
			"""
			/**
			* Override the default hashCode method for
			* all other objects to make hashCode
			*/
			@Override
			public int hashCode() {
				int result = 1;
			""".indent(DEFAULT_INDENT);

		bodyContent += statements;

		bodyContent +=
			"""
				long hashCode = result;
			$hashCodeManipulation
				return (int)hashCode;
			}
			""".replace("$hashCodeManipulation", HASH_CODE_MANIPULATION)
				.indent(DEFAULT_INDENT);

		String equalsStatements = "";
		// Generate a call to private method that iterates through fields
		// and calculates the hashcode.
		equalsStatements = Common.getFieldsEqualsStatements(fields, equalsStatements);

		bodyContent +=
		"""
		/**
		* Override the default equals method for
		*/
		@Override
		public boolean equals(Object that) {
		    if (that == null || this.getClass() != that.getClass()) {
		        return false;
		    }
		    $javaRecordName thatObj = ($javaRecordName)that;
		""".replace("$javaRecordName", javaRecordName).indent(DEFAULT_INDENT);

		bodyContent += equalsStatements.indent(DEFAULT_INDENT);
		bodyContent +=
		"""
		    return true;
		}
		""".indent(DEFAULT_INDENT);

		// Has methods
		bodyContent += String.join("\n", hasMethods);
		bodyContent += "\n";

		// oneof getters
		bodyContent += String.join("\n    ", oneofGetters);
		bodyContent += "\n";

		// builder copy & new builder methods
		bodyContent +=
    		"""
			/**
			 * Return a builder for building a copy of this model object. It will be pre-populated with all the data from this
			 * model object.
			 *
			 * @return a pre-populated builder
			 */
			public Builder copyBuilder() {
			    return new Builder(%s);
			}
			
			/**
			 * Return a new builder for building a model object. This is just a shortcut for <code>new Model.Builder()</code>.
			 *
			 * @return a new builder
			 */
			public static Builder newBuilder() {
			    return new Builder();
			}
			"""
			.formatted(fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", ")))
			.indent(DEFAULT_INDENT);

		// generate builder
		bodyContent += generateBuilder(msgDef, fields, lookupHelper);
		bodyContent += "\n";

		// oneof enums
		bodyContent += String.join("\n    ", oneofEnums);

		// === Build file
		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			javaWriter.write(
     				"""
					package $package;
					$imports
					import com.hedera.pbj.runtime.Codec;
					import java.util.function.Consumer;
					import edu.umd.cs.findbugs.annotations.Nullable;
					import static java.util.Objects.requireNonNull;
					
					$javaDocComment$deprecated
					public record $javaRecordName(
					$fields){
					$bodyContent}
					"""
					.replace("$package",modelPackage)
					.replace("$imports",imports.isEmpty() ? "" : imports.stream().collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")))
					.replace("$javaDocComment",javaDocComment)
					.replace("$deprecated", deprecated)
					.replace("$javaRecordName",javaRecordName)
					.replace("$fields", fields.stream().map(field ->
							(field.type() == FieldType.MESSAGE ? "@Nullable " : "")
									+ field.javaFieldType() + " " + field.nameCamelFirstLower()
					).collect(Collectors.joining(",\n")).indent(DEFAULT_INDENT))
					.replace("$bodyContent",bodyContent)
			);
		}
	}

	private static void generateBuilderMethods(List<String> builderMethods, Field field) {
		final String prefix, postfix, fieldToSet;
		final OneOfField parentOneOfField = field.parent();
		if (parentOneOfField != null) {
			final String oneOfEnumValue = parentOneOfField.getEnumClassRef()+"."+camelToUpperSnake(field.name());
			prefix = " new OneOf<>("+oneOfEnumValue+",";
			postfix = ")";
			fieldToSet = parentOneOfField.nameCamelFirstLower();
		} else {
			prefix = "";
			postfix = "";
			fieldToSet = field.nameCamelFirstLower();
		}
		builderMethods.add("""
						/**
						 * $fieldDoc
						 *
						 * @param $fieldName value to set
						 * @return builder to continue building with
						 */
						public Builder $fieldName($fieldType $fieldName) {
						    this.$fieldToSet = $prefix$fieldName$postfix;
						    return this;
						}"""
				.replace("$fieldDoc",field.comment()
						.replaceAll("\n", "\n * "))
				.replace("$fieldName",field.nameCamelFirstLower())
				.replace("$fieldToSet",fieldToSet)
				.replace("$prefix",prefix)
				.replace("$postfix",postfix)
				.replace("$fieldType",field.javaFieldType())
				.indent(DEFAULT_INDENT)
		);
		// add nice method for simple message fields so can just set using un-built builder
		if (field.type() == Field.FieldType.MESSAGE && !field.optionalValueType() && !field.repeated()) {
			builderMethods.add("""
						/**
						 * $fieldDoc
						 *
						 * @param builder A pre-populated builder
						 * @return builder to continue building with
						 */
						public Builder $fieldName($messageClass.Builder builder) {
						    this.$fieldToSet =$prefix builder.build() $postfix;
						    return this;
						}"""
					.replace("$messageClass",field.messageType())
					.replace("$fieldDoc",field.comment()
							.replaceAll("\n", "\n * "))
					.replace("$fieldName",field.nameCamelFirstLower())
					.replace("$fieldToSet",fieldToSet)
					.replace("$prefix",prefix)
					.replace("$postfix",postfix)
					.replace("$fieldType",field.javaFieldType())
					.indent(DEFAULT_INDENT)
			);
		}

		// add nice method for message fields with list types for varargs
		if (field.repeated()) {
			builderMethods.add("""
						/**
						 * $fieldDoc
						 *
						 * @param values varargs value to be built into a list
						 * @return builder to continue building with
						 */
						public Builder $fieldName($baseType ... values) {
						    this.$fieldToSet = $prefix List.of(values) $postfix;
						    return this;
						}"""
					.replace("$baseType",field.javaFieldType().substring("List<".length(),field.javaFieldType().length()-1))
					.replace("$fieldDoc",field.comment()
							.replaceAll("\n", "\n * "))
					.replace("$fieldName",field.nameCamelFirstLower())
					.replace("$fieldToSet",fieldToSet)
					.replace("$fieldType",field.javaFieldType())
					.replace("$prefix",prefix)
					.replace("$postfix",postfix)
					.indent(DEFAULT_INDENT)
			);
		}
	}

	private static String generateBuilder(final Protobuf3Parser.MessageDefContext msgDef, List<Field> fields, final ContextualLookupHelper lookupHelper) {
		final String javaRecordName = msgDef.messageName().getText();
		List<String> builderMethods = new ArrayList<>();
		for (Field field: fields) {
			if (field.type() == Field.FieldType.ONE_OF) {
				final OneOfField oneOfField = (OneOfField) field;
				for (Field subField: oneOfField.fields()) {
					generateBuilderMethods(builderMethods, subField);
				}
			} else {
				generateBuilderMethods(builderMethods, field);
			}
		}
		return """
			/**
			 * Builder class for easy creation, ideal for clean code where performance is not critical. In critical performance
			 * paths use the constructor directly.
			 */
			public static final class Builder {
			    $fields;
		
			    /**
			     * Create an empty builder
			     */
			    public Builder() {}
		
			    /**
			     * Create a pre-populated builder
			     * $constructorParamDocs
			     */
			    public Builder($constructorParams) {
			$constructorCode    }
		
			    /**
			     * Build a new model record with data set on builder
			     *
			     * @return new model record with data set
			     */
			    public $javaRecordName build() {
			        return new $javaRecordName($recordParams);
			    }
		
			    $builderMethods}"""
				.replace("$fields", fields.stream().map(field ->
						"private " + field.javaFieldType() + " " + field.nameCamelFirstLower() +
								" = " + getDefaultValue(field, msgDef, lookupHelper)
						).collect(Collectors.joining(";\n    ")))
				.replace("$constructorParamDocs",fields.stream().map(field ->
						"\n     * @param "+field.nameCamelFirstLower()+" "+
								field.comment().replaceAll("\n", "\n     *         "+" ".repeat(field.nameCamelFirstLower().length()))
						).collect(Collectors.joining(", ")))
				.replace("$constructorParams",fields.stream().map(field ->
						field.javaFieldType() + " " + field.nameCamelFirstLower()
						).collect(Collectors.joining(", ")))
				.replace("$constructorCode",fields.stream().map(field ->
						"this.$name = $name;".replace("$name", field.nameCamelFirstLower())
						).collect(Collectors.joining("\n")).indent(DEFAULT_INDENT * 2))
				.replace("$javaRecordName",javaRecordName)
				.replace("$recordParams",fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", ")))
				.replace("$builderMethods", String.join("\n", builderMethods))
				.indent(DEFAULT_INDENT);
	}

	private static String getDefaultValue(Field field, final Protobuf3Parser.MessageDefContext msgDef, final ContextualLookupHelper lookupHelper) {
		if (field.type() == Field.FieldType.ONE_OF) {
			return lookupHelper.getFullyQualifiedMessageClassname(FileType.CODEC, msgDef)+"."+field.javaDefault();
		} else {
			return field.javaDefault();
		}
	}

	private static String generateConstructorCode(final Field f) {
		StringBuilder sb = new StringBuilder("""
								if ($fieldName == null) {
								    throw new NullPointerException("Parameter '$fieldName' must be supplied and can not be null");
								}""".replace("$fieldName", f.nameCamelFirstLower()));
		if (f instanceof final OneOfField oof) {
			for (Field subField: oof.fields()) {
				if(subField.optionalValueType()) {
					sb.append("""
       
							// handle special case where protobuf does not have destination between a OneOf with optional
							// value of empty vs an unset OneOf.
							if($fieldName.kind() == $fieldUpperNameOneOfType.$subFieldNameUpper && $fieldName.value() == null) {
							$fieldName = new OneOf<>($fieldUpperNameOneOfType.UNSET, null);
							}"""
							.replace("$fieldName", f.nameCamelFirstLower())
							.replace("$fieldUpperName", f.nameCamelFirstUpper())
							.replace("$subFieldNameUpper", camelToUpperSnake(subField.name()))
					);
				}
			}
		}
		return sb.toString().indent(DEFAULT_INDENT);
	}
}