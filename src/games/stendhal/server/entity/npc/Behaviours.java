package games.stendhal.server.entity.npc;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import games.stendhal.server.*;
import games.stendhal.server.entity.Player;
import games.stendhal.server.entity.item.*;
import games.stendhal.server.rule.*;
import marauroa.common.*;
import marauroa.common.game.*;
import marauroa.server.game.*;
import org.apache.log4j.Logger;

public class Behaviours 
  {  
  /** the logger instance. */
  private static final Logger logger = Log4J.getLogger(Behaviours.class);

  private static RPServerManager rpman;
  private static StendhalRPRuleProcessor rules;
  private static StendhalRPWorld world;

  public static void initialize(RPServerManager rpman, StendhalRPRuleProcessor rules, RPWorld world)
    {
    Behaviours.rpman=rpman;
    Behaviours.rules=rules;
    Behaviours.world=(StendhalRPWorld)world;
    }
  
  public static void addGreeting(SpeakerNPC npc)
    {
    npc.add(0,new String[]{"hi","hello","greetings"}, 1,"Greetings! How may I help you?",null);
    }

  public static void addJob(SpeakerNPC npc, String jobDescription)
    {
    npc.add(1,new String[]{"job","work"}, 1,jobDescription,null);
    }

  public static void addHelp(SpeakerNPC npc, String helpDescription)
    {
    npc.add(1,new String[]{"help","ayuda"}, 1,helpDescription,null);
    }

  public static void addGoodbye(SpeakerNPC npc)
    {
    npc.add(-1,new String[]{"bye","farewell","cya"}, 0,"Bye.",null);
    }
  
  public static class SellerBehaviour
    {
    protected Map<String,Integer> items;
    protected String choosenItem;
    
    public SellerBehaviour(Map<String,Integer> items)
      {
      this.items=items;

// TODO: Enable this check correctly after 0.40 release      
//
//      EntityManager entityMan=world.getRuleManager().getEntityManager();
//      
//      for(String item: items.keySet())
//        {
//        if(!entityMan.isItem(item))
//          {
//          logger.warn("Seller trying to sell an unexisting item: "+item);
//          }
//        }
      }
      
    public Set<String> getItems()
      {
      return items.keySet();
      }
      
    public boolean hasItem(String item)
      {
      return items.containsKey(item);
      }
      
    public int getPrice(String item)
      {
      return items.get(item);
      }
    
    public void setChoosenItem(String item)
      {
      choosenItem=item;
      }
    
    public String getChoosenItem()
      {
      return choosenItem;
      }
      
    public int playerMoney(Player player)
      {
      int money=0;
      
      Iterator<RPSlot> it=player.slotsIterator();
      while(it.hasNext())
        {
        RPSlot slot=(RPSlot)it.next();
        for(RPObject object: slot)
          {
          if(object instanceof Money)
            {
            money+=((Money)object).getQuantity();
            }
          }
        }
      
      return money;
      }
  
    public boolean chargePlayer(Player player, int amount)
      {
      int left=amount;
      
      Iterator<RPSlot> it=player.slotsIterator();
      while(it.hasNext() && left!=0)
        {
        RPSlot slot=(RPSlot)it.next();
  
        Iterator<RPObject> object_it=slot.iterator();
        while(object_it.hasNext())
          {
          RPObject object=object_it.next();
          if(object instanceof Money)
            {
            int quantity=((Money)object).getQuantity();
            if(left>=quantity)
              {
              slot.remove(object.getID());
              left-=quantity;
              
              object_it=slot.iterator();
              }
            else
              {
              ((Money)object).setQuantity(quantity-left);
              left=0;
              break;
              }
            }
          }
        }
      
      world.modify(player);
      
      return left==0;
      }

    public boolean onSell(SpeakerNPC seller, Player player, String itemName, int itemPrice)
      {
      EntityManager manager = world.getRuleManager().getEntityManager();
       
      Item item=manager.getItem(itemName);
      if(item==null)
        {
        logger.error("Trying to sell an unexisting item: "+itemName);
        return false;
        }
        
      item.put("zoneid",player.get("zoneid"));
      IRPZone zone=world.getRPZone(player.getID());
      zone.assignRPObjectID(item);
      
      if(player.equip(item))
        {
        chargePlayer(player,itemPrice);
        seller.say("Congratulations! Here is your "+itemName+"!");
        return true;
        }
      else
        {
        seller.say("Sorry, but you cannot equip the "+itemName+".");
        return false;
        }
      }
    }
 
  public static void addSeller(SpeakerNPC npc, SellerBehaviour items)
    {
    npc.setBehaviourData("seller",items);
    
    StringBuffer st=new StringBuffer();
    for(String item: items.getItems())
      {
      st.append(item+",");
      }
       
    npc.add(1,"offer", 1,"I sell "+st.toString(),null);
    npc.add(1,"buy",20, null,new SpeakerNPC.ChatAction()
      {
      public void fire(Player player, String text, SpeakerNPC engine)
        {
        SellerBehaviour sellableItems=(SellerBehaviour)engine.getBehaviourData("seller");
        
        int i=text.indexOf(" ");
        String item=text.substring(i+1);
        
        if(sellableItems.hasItem(item))
          {
          int price=sellableItems.getPrice(item);
          sellableItems.setChoosenItem(item);

          engine.say(item+" costs "+price+". Do you want to buy?");
          }
        else
          {
          engine.say("Sorry, I don't sell "+item);
          engine.setActualState(1);          
          }
        }
      });
      
    npc.add(20,"yes", 1,"Thanks.",new SpeakerNPC.ChatAction()
      {
      public void fire(Player player, String text, SpeakerNPC engine)
        {
        SellerBehaviour sellableItems=(SellerBehaviour)engine.getBehaviourData("seller");

        String itemName=sellableItems.getChoosenItem();        
        int itemPrice=sellableItems.getPrice(itemName);

        if(sellableItems.playerMoney(player)<itemPrice)
          {
          engine.say("A real pity! You don't have enough money!");
          return;
          }
          
        logger.debug("Selling a "+itemName+" to player "+player.getName());
        
        sellableItems.onSell(engine,player,itemName, itemPrice);
        }
      });
    npc.add(20,"no", 1,"Ok, how may I help you?",null);
    }
    

  public static class BuyerBehaviour
    {
    private Map<String,Integer> items;
    private String choosenItem;
    
    public BuyerBehaviour(Map<String,Integer> items)
      {
      this.items=items;
      }
      
    public Set<String> getItems()
      {
      return items.keySet();
      }
      
    public boolean hasItem(String item)
      {
      return items.containsKey(item);
      }
      
    public int getPrice(String item)
      {
      return items.get(item);
      }
    
    public void setChoosenItem(String item)
      {
      choosenItem=item;
      }
    
    public String getChoosenItem()
      {
      return choosenItem;
      }
      
    public void payPlayer(Player player, int amount)
      {
      boolean found=false;
      Iterator<RPSlot> it=player.slotsIterator();
      while(it.hasNext())
        {
        RPSlot slot=(RPSlot)it.next();
  
        Iterator<RPObject> object_it=slot.iterator();
        while(object_it.hasNext())
          {
          RPObject object=object_it.next();
          if(object instanceof Money)
            {
            ((Money)object).add(amount);
            found=true;
            }
          }
        }
      
      if(!found)
        {
        RPSlot slot=player.getSlot("bag");
        Money money= new Money(amount);
        slot.assignValidID(money);
        slot.add(money);
        }
      
      world.modify(player);
      }
    
    private boolean removeItem(RPSlot slot, String itemName)
      {
      Iterator<RPObject> object_it=slot.iterator();
      while(object_it.hasNext())
        {
        RPObject object=object_it.next();
        if(object instanceof Item && object.get("name").equals(itemName))
          {
          slot.remove(object.getID());
          return true;
          }
        }
      
      return false;
      }

    public boolean removeItem(Player player, String itemName)
      {
      /* We iterate first the bag */
      if(removeItem(player.getSlot("bag"),itemName))
        {
        world.modify(player);      
        return true;
        }
        
      Iterator<RPSlot> it=player.slotsIterator();
      while(it.hasNext())
        {
        RPSlot slot=(RPSlot)it.next();
        
        if(removeItem(slot,itemName))
          {
          world.modify(player);      
          return true;
          }
        }
      
      return false;
      }

    public boolean onBuy(SpeakerNPC seller, Player player, String itemName, int itemPrice)
      {
      if(removeItem(player,itemName))
        {
        payPlayer(player,itemPrice);
        seller.say("Thanks!. Here is your money");
        return true;
        }
      else
        {
        seller.say("Sorry!. You don't have any "+itemName);
        return false;
        }
      }
    }


  public static void addBuyer(SpeakerNPC npc, BuyerBehaviour items)
    {
    npc.setBehaviourData("buyer",items);
    
    StringBuffer st=new StringBuffer();
    for(String item: items.getItems())
      {
      st.append(item+",");
      }
       
    npc.add(1,"offer", 1,"I buy "+st.toString(),null);
    npc.add(1,"sell",30, null,new SpeakerNPC.ChatAction()
      {
      public void fire(Player player, String text, SpeakerNPC engine)
        {
        BuyerBehaviour buyableItems=(BuyerBehaviour)engine.getBehaviourData("buyer");
        
        int i=text.indexOf(" ");
        String item=text.substring(i+1);
        
        if(buyableItems.hasItem(item))
          {
          int price=buyableItems.getPrice(item);
          buyableItems.setChoosenItem(item);

          engine.say(item+" is worth "+price+". Do you want to sell?");
          }
        else
          {
          engine.say("Sorry, I don't buy "+item);
          engine.setActualState(1);          
          }
        }
      });
      
    npc.add(30,"yes", 1,"Thanks.",new SpeakerNPC.ChatAction()
      {
      public void fire(Player player, String text, SpeakerNPC engine)
        {
        BuyerBehaviour buyableItems=(BuyerBehaviour)engine.getBehaviourData("buyer");

        String itemName=buyableItems.getChoosenItem();        
        int itemPrice=buyableItems.getPrice(itemName);

        logger.debug("Buying a "+itemName+" from player "+player.getName());
        
        buyableItems.onBuy(engine,player,itemName, itemPrice);
        }
      });
    npc.add(30,"no", 1,"Ok, how may I help you?",null);
    }

  public static class HealerBehaviour extends SellerBehaviour
    {
    public HealerBehaviour(int cost)
      {
      super(null);
      items=new HashMap<String,Integer>();
      items.put("heal",cost);
      }
    
    public void heal(Player player, SpeakerNPC engine)
      {
      player.setHP(player.getBaseHP());
      player.healPoison();
      world.modify(player);
      }
    }


  public static void addHealer(SpeakerNPC npc, int cost)
    {
    npc.setBehaviourData("healer",new HealerBehaviour(cost));
    
    npc.add(1,"offer", 1,"I heal",null);
    npc.add(1,"heal",40, null,new SpeakerNPC.ChatAction()
      {
      public void fire(Player player, String text, SpeakerNPC engine)
        {
        HealerBehaviour healer=(HealerBehaviour)engine.getBehaviourData("healer");
        int cost=healer.getPrice("heal");
        
        if(cost>0)
          {
          engine.say("Healing costs "+cost+". Do you want to pay?");
          }
        else
          {
          engine.say("You are healed. How may I help you?");
          healer.heal(player,engine);
          
          engine.setActualState(1);
          }
        }
      });
      
    npc.add(40,"yes", 1,"Thanks.",new SpeakerNPC.ChatAction()
      {
      public void fire(Player player, String text, SpeakerNPC engine)
        {
        HealerBehaviour healer=(HealerBehaviour)engine.getBehaviourData("healer");
        int itemPrice=healer.getPrice("heal");

        if(healer.playerMoney(player)<itemPrice)
          {
          engine.say("A real pity! You don't have enough money!");
          return;
          }
        
        healer.chargePlayer(player,itemPrice);        
        }
      });
    npc.add(40,"no", 1,"Ok, how may I help you?",null);
    }
  }
