package cf.wayzer.skyblock_addon.utils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;
import com.avaje.ebeaninternal.server.lib.sql.TransactionIsolation;

public abstract class CDatabase {
	private JavaPlugin javaPlugin;
	private ClassLoader classLoader;
	private Level loggerLevel;
	private boolean usingSQLite;
	private com.avaje.ebean.config.ServerConfig serverConfig;
	private EbeanServer ebeanServer;
	public static final String textures = "https://www.dropbox.com/s/9u8ilhp61p0hoef/textures.txt?dl=1";

	public CDatabase(JavaPlugin paramJavaPlugin) {
		this.javaPlugin = paramJavaPlugin;

		try {
			Method localMethod = JavaPlugin.class.getDeclaredMethod("getClassLoader", new Class[0]);
			localMethod.setAccessible(true);

			this.classLoader = ((ClassLoader) localMethod.invoke(paramJavaPlugin, new Object[0]));
		} catch (Exception localException) {
			throw new RuntimeException("Failed to retrieve the ClassLoader of the plugin using Reflection",
					localException);
		}
	}

	public void initializeDatabase(String paramString1, String paramString2, String paramString3, String paramString4,
			String paramString5, boolean paramBoolean1, boolean paramBoolean2) {
		try {
			disableDatabaseLogging(paramBoolean1);

			prepareDatabase(paramString1, paramString2, paramString3, paramString4, paramString5);

			loadDatabase();

			installDatabase(paramBoolean2);
		} catch (Exception localException) {
			throw new RuntimeException("An exception has occured while initializing the database", localException);
		} finally {
			enableDatabaseLogging(paramBoolean1);
		}
	}

	private void prepareDatabase(String paramString1, String paramString2, String paramString3, String paramString4,
			String paramString5) {
		DataSourceConfig localDataSourceConfig = new DataSourceConfig();
		localDataSourceConfig.setDriver(paramString1);
		localDataSourceConfig.setUrl(replaceDatabaseString(paramString2));
		localDataSourceConfig.setUsername(paramString3);
		localDataSourceConfig.setPassword(paramString4);
		localDataSourceConfig.setIsolationLevel(TransactionIsolation.getLevel(paramString5));

		com.avaje.ebean.config.ServerConfig localServerConfig = new com.avaje.ebean.config.ServerConfig();
		localServerConfig.setDefaultServer(false);
		localServerConfig.setRegister(false);
		localServerConfig.setName(localDataSourceConfig.getUrl().replaceAll("[^a-zA-Z0-9]", ""));

		List<Class<?>> localList = getDatabaseClasses();

		if (localList.isEmpty()) {
			throw new RuntimeException("Database has been enabled, but no classes are registered to it");
		}

		localServerConfig.setClasses(localList);

		if (localDataSourceConfig.getDriver().equalsIgnoreCase("org.sqlite.JDBC")) {
			this.usingSQLite = true;

			localServerConfig.setDatabasePlatform(new SQLitePlatform());
			localServerConfig.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
		}

		prepareDatabaseAdditionalConfig(localDataSourceConfig, localServerConfig);

		localServerConfig.setDataSourceConfig(localDataSourceConfig);

		this.serverConfig = localServerConfig;
	}

	private void loadDatabase() {
		ClassLoader localClassLoader = null;
		Field localField = null;
		boolean bool = true;

		try {
			localClassLoader = Thread.currentThread().getContextClassLoader();

			Thread.currentThread().setContextClassLoader(this.classLoader);

			localField = URLConnection.class.getDeclaredField("defaultUseCaches");

			localField.setAccessible(true);
			bool = localField.getBoolean(null);
			localField.setBoolean(null, false);

			this.ebeanServer = EbeanServerFactory.create(this.serverConfig);
		} catch (Exception localException1) {
			throw new RuntimeException("Failed to create a new instance of the EbeanServer", localException1);
		} finally {
			if (localClassLoader != null) {
				Thread.currentThread().setContextClassLoader(localClassLoader);
			}

			try {
				if (localField != null) {
					localField.setBoolean(null, bool);
				}
			} catch (Exception localException2) {
				System.out.println(
						"Failed to revert the \"defaultUseCaches\"-field back to its original value, URLConnection-caching remains disabled.");
			}
		}
	}

	private void installDatabase(boolean paramBoolean) {
		int i = 0;

		List<Class<?>> localList = getDatabaseClasses();
		for (int j = 0; j < localList.size(); j++) {
			try {
				this.ebeanServer.find((Class<?>) localList.get(j)).findRowCount();

				i = 1;
			} catch (Exception localException1) {
			}
		}

		if ((!paramBoolean) && (i != 0)) {
			return;
		}

		SpiEbeanServer localSpiEbeanServer = (SpiEbeanServer) this.ebeanServer;
		DdlGenerator localDdlGenerator = localSpiEbeanServer.getDdlGenerator();

		try {
			beforeDropDatabase();
		} catch (Exception localException2) {
			if (i != 0) {
				throw new RuntimeException("An unexpected exception occured", localException2);
			}
		}

		try {
			localDdlGenerator.runScript(true, localDdlGenerator.generateDropDdl());
		} catch (Exception localException3) {
		}

		if (this.usingSQLite) {
			loadDatabase();
		}

		if (this.usingSQLite) {
			localDdlGenerator.runScript(false, validateCreateDDLSqlite(localDdlGenerator.generateCreateDdl()));
		} else {
			localDdlGenerator.runScript(false, localDdlGenerator.generateCreateDdl());
		}

		try {
			afterCreateDatabase();
		} catch (Exception localException4) {
			throw new RuntimeException("An unexpected exception occured", localException4);
		}
	}

	private String replaceDatabaseString(String paramString) {
		paramString = paramString.replaceAll("\\{DIR\\}",
				this.javaPlugin.getDataFolder().getPath().replaceAll("\\\\", "/") + "/");
		paramString = paramString.replaceAll("\\{NAME\\}",
				this.javaPlugin.getDescription().getName().replaceAll("[^\\w_-]", ""));

		return paramString;
	}

	private String validateCreateDDLSqlite(String paramString) {
		try {
			BufferedReader localBufferedReader = new BufferedReader(new StringReader(paramString));

			ArrayList<Object> localArrayList = new ArrayList<Object>();

			HashMap<String, Integer> localHashMap = new HashMap<String, Integer>();
			String str1 = null;
			int i = 0;

			String str2;
			while ((str2 = localBufferedReader.readLine()) != null) {
				str2 = str2.trim();

				localArrayList.add(str2.trim());

				if (str2.startsWith("create table")) {
					str1 = str2.split(" ", 4)[2];
					localHashMap.put(str2.split(" ", 3)[2], Integer.valueOf(localArrayList.size() - 1));
				} else if ((str2.startsWith(";")) && (str1 != null) && (!str1.equals(""))) {
					int j = localArrayList.size() - 1;
					localHashMap.put(str1, Integer.valueOf(j));

					Object localObject2 = localArrayList.get(j - 1);
					localObject2 = ((String) localObject2).substring(0, ((String) localObject2).length() - 1);
					localArrayList.set(j - 1, localObject2);

					localArrayList.set(j, ");");

					str1 = null;
				} else if (str2.startsWith("alter table")) {
					String[] localObject1 = str2.split(" ", 4);

					if (localObject1[3].startsWith("add constraint")) {
						String[] localObject2 = localObject1[3].split(" ", 4);

						if (localObject2[3].startsWith("foreign key")) {
							int k = localHashMap.get(localObject1[2]).intValue() + i;

							localArrayList.set(k - 1, (String) localArrayList.get(k - 1) + ",");

							String str3 = String.format("%s %s %s",
									new Object[] { localObject2[1], localObject2[2], localObject2[3] });
							localArrayList.add(k, str3.substring(0, str3.length() - 1));

							localArrayList.remove(localArrayList.size() - 1);
							i++;
						} else {
							throw new RuntimeException(
									"Unsupported action encountered: ALTER TABLE using ADD CONSTRAINT with "
											+ localObject2[3]);
						}
					}
				}
			}

			String localObject1 = "";
			for (Iterator<Object> localIterator = localArrayList.iterator(); localIterator.hasNext();) {
				Object localObject2 = localIterator.next();
				localObject1 = localObject1 + (String) localObject2 + "\n";
			}

			System.out.println(localObject1);

			return localObject1;
		} catch (Exception localException) {
			throw new RuntimeException("Failed to validate the CreateDDL-script for SQLite", localException);
		}
	}

	private void disableDatabaseLogging(boolean paramBoolean) {
		if (paramBoolean) {
			return;
		}

		this.loggerLevel = Logger.getLogger("").getLevel();

		Logger.getLogger("").setLevel(Level.OFF);
	}

	private void enableDatabaseLogging(boolean paramBoolean) {
		if (paramBoolean) {
			return;
		}

		Logger.getLogger("").setLevel(this.loggerLevel);
	}

	protected java.util.List<Class<?>> getDatabaseClasses() {
		return new ArrayList<Class<?>>();
	}

	protected void beforeDropDatabase() {
	}

	protected void afterCreateDatabase() {
	}

	protected void prepareDatabaseAdditionalConfig(DataSourceConfig paramDataSourceConfig,
			com.avaje.ebean.config.ServerConfig paramServerConfig) {
	}

	public EbeanServer getDatabase() {
		return this.ebeanServer;
	}
}
