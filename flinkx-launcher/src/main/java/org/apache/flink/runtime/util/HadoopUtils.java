/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.util;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collection;

/**
 * Utility class for working with Hadoop-related classes. This should only be used if Hadoop
 * is on the classpath.
 */
public class HadoopUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HadoopUtils.class);

    private static final Text HDFS_DELEGATION_TOKEN_KIND = new Text("HDFS_DELEGATION_TOKEN");

    public static final String HADOOP_CONF_BYTES = "hadoop.conf.bytes";

    public static Configuration getHadoopConfiguration(org.apache.flink.configuration.Configuration flinkConfiguration) {

        // Instantiate a HdfsConfiguration to load the hdfs-site.xml and hdfs-default.xml
        // from the classpath
        Configuration result = new HdfsConfiguration();
        boolean foundHadoopConfiguration = false;

        // We need to load both core-site.xml and hdfs-site.xml to determine the default fs path and
        // the hdfs configuration
        // Try to load HDFS configuration from Hadoop's own configuration files
        // 1. approach: Flink configuration
        final String hdfsDefaultPath =
                flinkConfiguration.getString(ConfigConstants.HDFS_DEFAULT_CONFIG, null);

        if (hdfsDefaultPath != null) {
            result.addResource(new org.apache.hadoop.fs.Path(hdfsDefaultPath));
            LOG.debug("Using hdfs-default configuration-file path form Flink config: {}", hdfsDefaultPath);
            foundHadoopConfiguration = true;
        } else {
            LOG.debug("Cannot find hdfs-default configuration-file path in Flink config.");
        }

        final String hdfsSitePath = flinkConfiguration.getString(ConfigConstants.HDFS_SITE_CONFIG, null);
        if (hdfsSitePath != null) {
            result.addResource(new org.apache.hadoop.fs.Path(hdfsSitePath));
            LOG.debug("Using hdfs-site configuration-file path form Flink config: {}", hdfsSitePath);
            foundHadoopConfiguration = true;
        } else {
            LOG.debug("Cannot find hdfs-site configuration-file path in Flink config.");
        }

        // 2. Approach environment variables
        String[] possibleHadoopConfPaths = new String[4];
        final String hadoopHome = System.getenv("HADOOP_HOME");
        if (hadoopHome != null) {
            possibleHadoopConfPaths[0] = hadoopHome + "/conf";
            possibleHadoopConfPaths[1] = hadoopHome + "/etc/hadoop"; // hadoop 2.2
        }
        possibleHadoopConfPaths[2] = System.getenv("HADOOP_CONF_DIR");
        possibleHadoopConfPaths[3] = flinkConfiguration.getString(ConfigConstants.PATH_HADOOP_CONFIG, null);

        for (String possibleHadoopConfPath : possibleHadoopConfPaths) {
            if (possibleHadoopConfPath != null) {
                if (new File(possibleHadoopConfPath).exists()) {
                    if (new File(possibleHadoopConfPath + "/core-site.xml").exists()) {
                        result.addResource(new org.apache.hadoop.fs.Path(possibleHadoopConfPath + "/core-site.xml"));
                        LOG.debug("Adding " + possibleHadoopConfPath + "/core-site.xml to hadoop configuration");
                        foundHadoopConfiguration = true;
                    }
                    if (new File(possibleHadoopConfPath + "/hdfs-site.xml").exists()) {
                        result.addResource(new org.apache.hadoop.fs.Path(possibleHadoopConfPath + "/hdfs-site.xml"));
                        LOG.debug("Adding " + possibleHadoopConfPath + "/hdfs-site.xml to hadoop configuration");
                        foundHadoopConfiguration = true;
                    }
                }
            }
        }

        if (!foundHadoopConfiguration) {
            LOG.debug("Could not find Hadoop configuration via any of the supported methods " +
                    "(Flink configuration, environment variables).");
        }

        return result;
    }

    /**
     * Indicates whether the current user has an HDFS delegation token.
     */
    public static boolean hasHDFSDelegationToken() throws Exception {
        UserGroupInformation loginUser = UserGroupInformation.getCurrentUser();
        Collection<Token<? extends TokenIdentifier>> usrTok = loginUser.getTokens();
        for (Token<? extends TokenIdentifier> token : usrTok) {
            if (token.getKind().equals(HDFS_DELEGATION_TOKEN_KIND)) {
                return true;
            }
        }
        return false;
    }

    public static Configuration deserializeHadoopConf(byte[] bytes) {
        Configuration hadoopConf = new Configuration();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        DataInputStream datain = new DataInputStream(in);
        try {
            hadoopConf.readFields(datain);
            return hadoopConf;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(datain != null) {
                try {
                    datain.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if(in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return null;
    }

    public static byte[] serializeHadoopConf(Configuration hadoopConf)  {
        ByteArrayOutputStream out =  new ByteArrayOutputStream();
        DataOutputStream dataout = new DataOutputStream(out);
        try {
            hadoopConf.write(dataout);
            return out.toByteArray();
        } catch(IOException ex) {
            return null;
        } finally {

            if(dataout != null) {
                try {
                    dataout.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if(out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }

    }
}
