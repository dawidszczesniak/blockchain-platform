config.stats = 'errors-only';

config.infrastructureLogging = {
  ...(config.infrastructureLogging || {}),
  level: 'error',
};

config.devServer = {
  ...(config.devServer || {}),
  devMiddleware: {
    ...((config.devServer && config.devServer.devMiddleware) || {}),
    stats: 'errors-only',
  },
  client: {
    ...((config.devServer && config.devServer.client) || {}),
    logging: 'error',
  },
};
