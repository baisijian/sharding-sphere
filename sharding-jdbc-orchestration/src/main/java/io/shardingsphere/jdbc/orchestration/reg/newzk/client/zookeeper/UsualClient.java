/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper;

import io.shardingsphere.jdbc.orchestration.reg.newzk.client.action.IExecStrategy;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.action.ITransactionProvider;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.utility.ZookeeperConstants;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.base.BaseClient;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.base.BaseContext;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.provider.TransactionProvider;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.section.ClientContext;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.section.StrategyType;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.strategy.AsyncRetryStrategy;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.strategy.ContentionStrategy;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.strategy.SyncRetryStrategy;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.strategy.TransactionContendStrategy;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.strategy.UsualStrategy;
import io.shardingsphere.jdbc.orchestration.reg.newzk.client.zookeeper.transaction.BaseTransaction;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Usually use client.
 *
 * @author lidongbo
 */
@Slf4j
public class UsualClient extends BaseClient {
    
    private final Map<StrategyType, IExecStrategy> strategies = new ConcurrentHashMap<>();
    
    @Getter
    private IExecStrategy strategy;
    
    protected UsualClient(final BaseContext context) {
        super(context);
    }
    
    @Override
    public void close() {
        strategies.clear();
        super.close();
    }
    
    @Override
    public synchronized void useExecStrategy(final StrategyType strategyType) {
        log.debug("useExecStrategy:{}", strategyType);
        if (strategies.containsKey(strategyType)) {
            strategy = strategies.get(strategyType);
            return;
        }
        
        ITransactionProvider provider = new TransactionProvider(getRootNode(), getHolder(), ZookeeperConstants.WATCHED, getAuthorities());
        switch (strategyType) {
            case USUAL:
                strategy = new UsualStrategy(provider);
                break;
            case CONTEND:
                strategy = new ContentionStrategy(provider);
                break;
            case TRANSACTION_CONTEND:
                strategy = new TransactionContendStrategy(provider);
                break;
            case SYNC_RETRY:
                strategy = new SyncRetryStrategy(provider, ((ClientContext) getContext()).getDelayRetryPolicy());
                break;
            case ASYNC_RETRY:
                strategy = new AsyncRetryStrategy(provider, ((ClientContext) getContext()).getDelayRetryPolicy());
                break;
            default:
                strategy = new UsualStrategy(provider);
                break;
        }
        
        strategies.put(strategyType, strategy);
    }
    
    @Override
    public String getDataString(final String key) throws KeeperException, InterruptedException {
        return strategy.getDataString(key);
    }
    
    @Override
    public byte[] getData(final String key) throws KeeperException, InterruptedException {
        return strategy.getData(key);
    }
    
    @Override
    public void getData(final String key, final AsyncCallback.DataCallback callback, final Object ctx) throws KeeperException, InterruptedException {
        strategy.getData(key, callback, ctx);
    }
    
    @Override
    public boolean checkExists(final String key) throws KeeperException, InterruptedException {
        return strategy.checkExists(key);
    }
    
    @Override
    public boolean checkExists(final String key, final Watcher watcher) throws KeeperException, InterruptedException {
        return strategy.checkExists(key, watcher);
    }
    
    @Override
    public List<String> getChildren(final String key) throws KeeperException, InterruptedException {
        return strategy.getChildren(key);
    }
    
    @Override
    public void createCurrentOnly(final String key, final String value, final CreateMode createMode) throws KeeperException, InterruptedException {
        createNamespace();
        if (getRootNode().equals(key)) {
            return;
        }
        strategy.createCurrentOnly(key, value, createMode);
    }
    
    @Override
    public void createAllNeedPath(final String key, final String value, final CreateMode createMode) throws KeeperException, InterruptedException {
        createNamespace();
        if (getRootNode().equals(key)) {
            return;
        }
        strategy.createAllNeedPath(key, value, createMode);
    }
    
    @Override
    public void update(final String key, final String value) throws KeeperException, InterruptedException {
        strategy.update(key, value);
    }
    
    @Override
    public void deleteOnlyCurrent(final String key) throws KeeperException, InterruptedException {
        if (getRootNode().equals(key)) {
            deleteNamespace();
            return;
        }
        strategy.deleteOnlyCurrent(key);
    }
    
    @Override
    public void deleteOnlyCurrent(final String key, final AsyncCallback.VoidCallback callback, final Object ctx) throws KeeperException, InterruptedException {
        if (getRootNode().equals(key)) {
            deleteNamespace();
            return;
        }
        strategy.deleteOnlyCurrent(key, callback, ctx);
    }
    
    @Override
    public void deleteAllChildren(final String key) throws KeeperException, InterruptedException {
        strategy.deleteAllChildren(key);
        if (getRootNode().equals(key)) {
            setRootExist(false);
            log.debug("deleteAllChildren delete root: {}", getRootNode());
        }
    }
    
    @Override
    public void deleteCurrentBranch(final String key) throws KeeperException, InterruptedException {
        strategy.deleteCurrentBranch(key);
        if (!strategy.checkExists(getRootNode())) {
            setRootExist(false);
            log.debug("deleteCurrentBranch delete root: {}", getRootNode());
        }
    }
    
    @Override
    public BaseTransaction transaction() {
        return strategy.transaction();
    }
}
