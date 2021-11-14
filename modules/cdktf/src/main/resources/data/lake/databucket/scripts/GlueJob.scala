package data.lake.databucket.scripts

object GlueApp {
  def main(sysArgs: Array[String]) {
    val spark: SparkContext = new SparkContext()
    val glueContext: GlueContext = new GlueContext(spark)
    // @params: [JOB_NAME]
    val args = GlueArgParser.getResolvedOptions(sysArgs, Seq("JOB_NAME").toArray)
    Job.init(args("JOB_NAME"), glueContext, args.asJava)
    // @type: DataSource
    // @args: [database = "movies-db", table_name = "data", transformation_ctx = "datasource0"]
    // @return: datasource0
    // @inputs: []
    val datasource0 = glueContext.getCatalogSource(database = "movies-db", tableName = "data", redshiftTmpDir = "", transformationContext = "datasource0").getDynamicFrame()
    // @type: ApplyMapping
    // @args: [mapping = [("movieid", "long", "movieid", "long"), ("title", "string", "title", "string"), ("genres", "string", "genres", "string")], transformation_ctx = "applymapping1"]
    // @return: applymapping1
    // @inputs: [frame = datasource0]
    val applymapping1 = datasource0.applyMapping(mappings = Seq(("movieid", "long", "movieid", "long"), ("title", "string", "title", "string"), ("genres", "string", "genres", "string")), caseSensitive = false, transformationContext = "applymapping1")
    // @type: SelectFields
    // @args: [paths = ["movieid", "title", "genres"], transformation_ctx = "selectfields2"]
    // @return: selectfields2
    // @inputs: [frame = applymapping1]
    val selectfields2 = applymapping1.selectFields(paths = Seq("movieid", "title", "genres"), transformationContext = "selectfields2")
    // @type: ResolveChoice
    // @args: [choice = "MATCH_CATALOG", database = "movies-db", table_name = "data", transformation_ctx = "resolvechoice3"]
    // @return: resolvechoice3
    // @inputs: [frame = selectfields2]
    val resolvechoice3 = selectfields2.resolveChoice(choiceOption = Some(ChoiceOption("MATCH_CATALOG")), database = Some("movies-db"), tableName = Some("data"), transformationContext = "resolvechoice3")
    // @type: DataSink
    // @args: [database = "movies-db", table_name = "data", transformation_ctx = "datasink4"]
    // @return: datasink4
    // @inputs: [frame = resolvechoice3]
    val datasink4 = glueContext.getCatalogSink(database = "movies-db", tableName = "data", redshiftTmpDir = "", transformationContext = "datasink4").writeDynamicFrame(resolvechoice3)
    Job.commit()
  }
}