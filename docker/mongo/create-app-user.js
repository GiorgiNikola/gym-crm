// Runs once, from /docker-entrypoint-initdb.d, only when /data/db is empty.
const required = ['MONGO_APP_DATABASE', 'MONGO_APP_USERNAME', 'MONGO_APP_PASSWORD'];
const missing = required.filter((name) => !process.env[name]);
if (missing.length > 0) {
  throw new Error('create-app-user.js: missing ' + missing.join(', '));
}

const database = process.env.MONGO_APP_DATABASE;
db.getSiblingDB(database).createUser({
  user: process.env.MONGO_APP_USERNAME,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: 'readWrite', db: database }],
});
print('create-app-user.js: created ' + process.env.MONGO_APP_USERNAME + ' with readWrite on ' + database);
