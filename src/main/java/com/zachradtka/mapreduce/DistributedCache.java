package com.zachradtka.mapreduce;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class DistributedCache extends Configured implements Tool {

	/**
	 * Option string to identify file used to ignore patterns
	 */
	protected static final String ignorePatternsOptionString = "distributedcache.ignore.patterns";

	/**
	 * Option string to identify if case sensitive compare should be used
	 */
	protected static final String caseSensitiveOptionString = "distributedcache.case.sensitive";

	protected static final String patternFileFlag = "-patternFile";

	/**
	 * Create a stop list from the files located in the distributed cache. Tokenize the value and
	 * output each word as a key and 1 as the value.
	 * 
	 * @author Zachary Radtka
	 *
	 */
	public static class DistributedCacheMapper extends
			Mapper<LongWritable, Text, Text, LongWritable> {

		// The count for a single word, 1
		private final static LongWritable one = new LongWritable(1);

		// The word to pass to the combiner/reducer
		private Text word = new Text();

		// A set of words to ignore
		private Set<String> ignorePatterns = new HashSet<String>();

		// Perform a case sensitive search
		private Boolean caseSensitive = false;

		public void setup(Context context) throws IOException {

			Configuration conf = context.getConfiguration();

			// Determine if the word count should be case sensitive
			caseSensitive = conf.getBoolean(caseSensitiveOptionString, false);

			if (conf.getBoolean(ignorePatternsOptionString, false)) {

				// Get the list of cache files from the context
				URI[] files = context.getCacheFiles();

				// Loop through the list of cache files adding all of the words
				// to the stop list
				for (URI file : files) {
					Path filePath = new Path(file.getPath());
					String fileName = filePath.getName().toString();

					try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
						String line;

						while ((line = br.readLine()) != null) {
							String pattern = (caseSensitive) ? line : line.toLowerCase();
							ignorePatterns.add(pattern);
						}
					}
				}
			}
		}

		public void map(LongWritable keyIn, Text valueIn, Context context) throws IOException,
				InterruptedException {

			// Convert the entire line to lower case if the caseSensitive flag was set.
			String line = (caseSensitive) ? valueIn.toString() : valueIn.toString().toLowerCase();

			// Replace all of the patterns in the ignoreSet with ""
			for (String pattern : ignorePatterns) {
				line = line.replaceAll(pattern, "");
			}

			// Split the current line into words
			StringTokenizer tokenizer = new StringTokenizer(line);

			// Create an output key/value pair for each word: <word,1>
			while (tokenizer.hasMoreTokens()) {
				word.set(tokenizer.nextToken());
				context.write(word, one);
			}
		}
	}

	/**
	 * Sum up the values for each unique key
	 * 
	 * @author Zachary Radtka
	 *
	 */
	public static class DistributedCacheReducer extends
			Reducer<Text, LongWritable, Text, LongWritable> {

		// The total count for a unique word
		private LongWritable sum = new LongWritable();

		public void reduce(Text keyIn, Iterable<LongWritable> values, Context context)
				throws IOException, InterruptedException {

			// Initialize the current word's sum
			long partialSum = 0;

			// Calculate the number of occurrences for the current word
			for (LongWritable value : values) {
				partialSum += value.get();
			}

			// Set the sum for the current word and output it's total
			sum.set(partialSum);
			context.write(keyIn, sum);
		}
	}

	@Override
	public int run(String[] args) throws Exception {

		// Configuration processed by ToolRunner
		Configuration conf = getConf();

		// Create a Job using the processed conf
		Job job = Job.getInstance(conf, "DistributedCache");
		job.setJarByClass(DistributedCache.class);

		// Set the Mapper, Combiner, and Reducer class
		// It is important to note that when using the reducer as a
		// combiner, the reducer's input key/value types much match
		// it's output key/value types
		job.setMapperClass(DistributedCacheMapper.class);
		job.setCombinerClass(DistributedCacheReducer.class);
		job.setReducerClass(DistributedCacheReducer.class);

		// Set the job's output key and value
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(LongWritable.class);

		// Set the input and output paths
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));


		for (int i = 0; i < args.length; i++) {
			if (args[i].equals(patternFileFlag)) {

				// Set the distributed cache file
				job.addCacheFile(new Path(args[++i]).toUri());
				job.getConfiguration().setBoolean(ignorePatternsOptionString, true);
			}
		}

		// Submit the job and return it's status
		return job.waitForCompletion(true) ? 0 : 1;
	}

	/**
	 * A simple driver that creates, configures and runs a MapReduce job
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("<inputDirectory> <ouputDirectory> [" + patternFileFlag
					+ " filename]");
			System.exit(1);
		}

		// Set up and run the job
		int result = ToolRunner.run(new Configuration(), new DistributedCache(), args);

		System.exit(result);
	}

}
