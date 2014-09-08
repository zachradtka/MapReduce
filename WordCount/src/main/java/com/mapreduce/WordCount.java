package com.mapreduce;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class WordCount {

	/**
	 * Tokenize the value and output each word as a key and 1 as the value
	 * @author zradtka
	 *
	 */
	public static class WordCountMapper extends Mapper<LongWritable, Text, Text, LongWritable>{
		
		// The count for a single word, 1
		private final static LongWritable one = new LongWritable(1);
		
		// The word to pass to the combiner/reducer
		private Text word = new Text();
		
		public void map(LongWritable keyIn, Text valueIn, Context context) throws IOException, InterruptedException {
			// Split the current line into words
			StringTokenizer tokenizer = new StringTokenizer(valueIn.toString());
			
			// Create an output key/value pair for each word: <word,1>
			while (tokenizer.hasMoreTokens()) {
				word.set(tokenizer.nextToken().toLowerCase());
				context.write(word, one);
			}
		}
	}
	
	
	/**
	 * Sum up the values for each unique key
	 * @author zradtka
	 *
	 */
	public static class WordCountReducer extends Reducer<Text, LongWritable, Text, LongWritable>{
		
		private LongWritable sum = new LongWritable();
		
		public void reduce(Text keyIn, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException{
			long partialSum = 0;
			
			for (LongWritable value : values) {
				partialSum += value.get();
			}
			sum.set(partialSum);
			context.write(keyIn, sum);
		}
	}
	

	/**
	 * A simple driver that creates, configures and runs a MapReduce job 
	 * @param args
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {

		// Ensure an input and output path were specified
		if (args.length != 2) {
			System.exit(-1);
		}
		
		// Configure the Job
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "Word Count");
		job.setJarByClass(WordCount.class);
		
		// Set the Mapper, Combiner, and Reducer class
		// It is important to note that when using the reducer as a
		// combiner, the reducer's input key/value types much match		
		// it's output key/value types
		job.setMapperClass(WordCountMapper.class);
		job.setCombinerClass(WordCountReducer.class);
		job.setReducerClass(WordCountReducer.class);
		
		// Set the input and output for the job
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(LongWritable.class);
		
		// Set the input and output paths
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
		// Run the job
		System.exit(job.waitForCompletion(true) ? 0 : 1);

	}

}
